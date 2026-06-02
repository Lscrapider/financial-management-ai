from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.evidence import build_evidence
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, noisy_or, number, weighted_sum


class RiskStrategyProcessor:
    MODULE = "risk_strategy"

    def process(
        self,
        context: SceneAnalysisContext,
        trend_tags: dict[str, float],
        valuation_tags: dict[str, float],
        sentiment_tags: dict[str, float],
    ) -> SceneModuleResult:
        base = context.base_metrics
        low_volume = self._low_volume(context)
        chase_high_risk = weighted_sum(
            {
                "price_rise": number(base.get("price_rise")),
                "near_recent_high": number(base.get("near_recent_high")),
                "volume_expand": number(base.get("volume_expand")),
                "high_turnover": number(base.get("high_turnover")),
                "short_term_emotion": number(sentiment_tags.get("short_term_emotion")),
            },
            self._weights(context, "chase_high_risk_weights"),
        )
        false_breakout_risk = weighted_sum(
            {
                "breakout": number(base.get("breakout")),
                "close_weak": number(base.get("close_weak")),
                "upper_shadow": number(base.get("upper_shadow")),
                "volume_expand": number(base.get("volume_expand")),
            },
            self._weights(context, "false_breakout_risk_weights"),
        )
        liquidity_risk = weighted_sum(
            {
                "low_turnover": number(base.get("low_turnover")),
                "low_volume": low_volume,
            },
            self._weights(context, "liquidity_risk_weights"),
        )
        drawdown_risk = weighted_sum(
            {
                "near_recent_high": number(base.get("near_recent_high")),
                "volatility": number(base.get("volatility")),
                "price_rise": number(base.get("price_rise")),
                "support_distance": number(base.get("support_distance")),
            },
            self._weights(context, "drawdown_risk_weights"),
        )
        overheated_risk = weighted_sum(
            {
                "price_rise": number(base.get("price_rise")),
                "volume_expand": number(base.get("volume_expand")),
                "high_turnover": number(base.get("high_turnover")),
                "short_term_emotion": number(sentiment_tags.get("short_term_emotion")),
            },
            self._weights(context, "overheated_risk_weights"),
        )
        risk_control = noisy_or([chase_high_risk, false_breakout_risk, liquidity_risk, drawdown_risk, overheated_risk])
        uncertainty = self._uncertainty(context, sentiment_tags)
        position_control = weighted_sum(
            {
                "risk_control": risk_control,
                "volatility": number(base.get("volatility")),
                "uncertainty": uncertainty,
            },
            self._weights(context, "position_control_weights"),
        )
        take_profit_plan = weighted_sum(
            {
                "price_rise": number(base.get("price_rise")),
                "near_recent_high": number(base.get("near_recent_high")),
                "overheated_risk": overheated_risk,
                "drawdown_risk": drawdown_risk,
            },
            self._weights(context, "take_profit_plan_weights"),
        )
        stop_loss_plan = weighted_sum(
            {
                "break_recent_low": number(base.get("break_recent_low")),
                "downtrend": number(trend_tags.get("downtrend")),
                "panic_selling": number(sentiment_tags.get("panic_selling")),
                "drawdown_risk": drawdown_risk,
            },
            self._weights(context, "stop_loss_plan_weights"),
        )
        wait_confirm = self._wait_confirm(context, sentiment_tags)
        observe_next_day = max(
            number(sentiment_tags.get("market_attention_rise")) or 0.0,
            number(base.get("volume_spike")) or 0.0,
            number(base.get("large_intraday_move")) or 0.0,
        ) * (1 - self._trend_confirmed(context))
        avoid_emotional_trade = noisy_or([
            number(sentiment_tags.get("short_term_emotion")),
            number(sentiment_tags.get("panic_selling")),
            number(sentiment_tags.get("herding_effect")),
            overheated_risk,
        ])
        tags = active_tags({
            "chase_high_risk": chase_high_risk,
            "false_breakout_risk": false_breakout_risk,
            "liquidity_risk": liquidity_risk,
            "drawdown_risk": drawdown_risk,
            "overheated_risk": overheated_risk,
            "risk_control": risk_control,
            "position_control": position_control,
            "wait_confirm": wait_confirm,
            "observe_next_day": observe_next_day,
            "avoid_emotional_trade": avoid_emotional_trade,
            "take_profit_plan": take_profit_plan,
            "stop_loss_plan": stop_loss_plan,
        })
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction="risk" if score >= 0.3 else "neutral",
            tags=tags,
            evidence=self._evidence(tags),
        )

    def _weights(self, context: SceneAnalysisContext, key: str) -> dict:
        weights = context.risk_strategy_config.get(key)
        return weights if isinstance(weights, dict) else {}

    def _low_volume(self, context: SceneAnalysisContext) -> float | None:
        volume_ratio_5d = number(context.base_metrics.get("volume_ratio_5d"))
        if volume_ratio_5d is None:
            return None
        return clamp((1 - volume_ratio_5d) / 0.5)

    def _trend_confirmed(self, context: SceneAnalysisContext) -> float:
        ma5 = number(context.base_metrics.get("ma5"))
        ma10 = number(context.base_metrics.get("ma10"))
        ma20 = number(context.base_metrics.get("ma20"))
        current_price = number(context.base_metrics.get("current_price") or context.base_metrics.get("latest_price"))
        if None in (ma5, ma10, ma20, current_price):
            return 0.0
        return 1.0 if ma5 > ma10 > ma20 and current_price > ma5 else 0.0

    def _wait_confirm(self, context: SceneAnalysisContext, sentiment_tags: dict[str, float]) -> float:
        breakout = number(context.base_metrics.get("breakout")) or 0.0
        volume_expand = number(context.base_metrics.get("volume_expand")) or 0.0
        short_term_emotion = number(sentiment_tags.get("short_term_emotion")) or 0.0
        trend_confirmed = self._trend_confirmed(context)
        return noisy_or([
            breakout * (1 - volume_expand),
            short_term_emotion * (1 - trend_confirmed),
        ])

    def _uncertainty(self, context: SceneAnalysisContext, sentiment_tags: dict[str, float]) -> float | None:
        return weighted_sum(
            {
                "sentiment_conflict": self._sentiment_conflict(context, sentiment_tags),
            },
            self._weights(context, "uncertainty_weights"),
        )

    def _sentiment_conflict(self, context: SceneAnalysisContext, sentiment_tags: dict[str, float]) -> float:
        short_term_emotion = number(sentiment_tags.get("short_term_emotion")) or 0.0
        price_drop = number(context.base_metrics.get("price_drop")) or 0.0
        panic_selling = number(sentiment_tags.get("panic_selling")) or 0.0
        price_rise = number(context.base_metrics.get("price_rise")) or 0.0
        return clamp(max(short_term_emotion * price_drop, panic_selling * price_rise))

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "chase_high_risk": "上涨、高位、放量、高换手或短线情绪升温共同提高追高风险，chase_high_risk 标签触发",
            "false_breakout_risk": "突破信号同时伴随收盘偏弱、上影线或量能异常，false_breakout_risk 标签触发",
            "liquidity_risk": "低换手或低成交量代理信号显示交易活跃度不足，liquidity_risk 标签触发",
            "drawdown_risk": "高位、波动率、上涨强度和支撑距离共同提高回撤风险，drawdown_risk 标签触发",
            "overheated_risk": "上涨、放量、高换手或短线情绪升温共同提高过热风险，overheated_risk 标签触发",
            "risk_control": "多个风险子标签累计后达到风险控制阈值，risk_control 标签触发",
            "position_control": "风险控制、波动率或不确定性信号提示需要控制仓位，position_control 标签触发",
            "wait_confirm": "突破、量能或短线情绪信号尚未形成充分确认，wait_confirm 标签触发",
            "observe_next_day": "关注度、爆量或日内波动提示需要观察次日延续性，observe_next_day 标签触发",
            "avoid_emotional_trade": "短线情绪、恐慌、交易拥挤或过热风险提示避免情绪化交易，avoid_emotional_trade 标签触发",
            "take_profit_plan": "上涨、高位、过热或回撤风险提示需要预设止盈计划，take_profit_plan 标签触发",
            "stop_loss_plan": "跌破前低、下降趋势、恐慌抛售或回撤风险提示需要预设止损计划，stop_loss_plan 标签触发",
        }
        return build_evidence(tags, reasons)
