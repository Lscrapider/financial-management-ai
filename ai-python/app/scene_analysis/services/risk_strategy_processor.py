from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.evidence import active_signal_names, build_evidence, joined_signal_reason
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, noisy_or, number, weighted_sum
from app.scene_analysis.services.tag_applicability import apply_tag_applicability


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
        convertible_forced_redeem_risk = self._convertible_forced_redeem_risk(context)
        convertible_low_rating_risk = self._convertible_low_rating_risk(context)
        convertible_small_balance_risk = self._convertible_small_balance_risk(context)
        convertible_liquidity_risk = self._convertible_liquidity_risk(
            context,
            low_turnover=number(base.get("low_turnover")),
            low_volume=low_volume,
            small_balance_risk=convertible_small_balance_risk,
        )
        risk_control = noisy_or([
            chase_high_risk,
            false_breakout_risk,
            liquidity_risk,
            drawdown_risk,
            overheated_risk,
            convertible_forced_redeem_risk,
            convertible_low_rating_risk,
            convertible_small_balance_risk,
            convertible_liquidity_risk,
        ])
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
        tags = apply_tag_applicability(context, active_tags({
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
            "convertible_forced_redeem_risk": convertible_forced_redeem_risk,
            "convertible_low_rating_risk": convertible_low_rating_risk,
            "convertible_small_balance_risk": convertible_small_balance_risk,
            "convertible_liquidity_risk": convertible_liquidity_risk,
        }))
        score = module_score(tags)
        evidence_signals = {
            **context.base_metrics.values,
            **trend_tags,
            **valuation_tags,
            **sentiment_tags,
            **tags,
            "low_volume": low_volume,
            "uncertainty": uncertainty,
            "trend_confirmed": self._trend_confirmed(context),
            "redeem_trigger_progress": number(base.get("redeem_trigger_progress")),
            "bond_rating": base.get("bond_rating"),
            "remaining_size": number(base.get("remaining_size")),
        }
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction="risk" if score >= 0.3 else "neutral",
            tags=tags,
            evidence=self._evidence(tags, evidence_signals),
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

    def _convertible_forced_redeem_risk(self, context: SceneAnalysisContext) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        status = str(context.base_metrics.get("redeem_status") or "").lower()
        if any(keyword in status for keyword in ["triggered", "announced", "已触发", "已公告", "强赎"]):
            return 1.0
        progress = number(context.base_metrics.get("redeem_trigger_progress"))
        threshold = number(context.convertible_bond_config.get("redeem_progress_threshold"))
        if progress is None:
            return None
        if threshold is None or threshold <= 0:
            return clamp(progress)
        return clamp(progress / threshold)

    def _convertible_low_rating_risk(self, context: SceneAnalysisContext) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        rating = str(context.base_metrics.get("bond_rating") or "").strip().upper()
        if not rating:
            return None
        low_levels = context.convertible_bond_config.get("low_rating_levels")
        if not isinstance(low_levels, list):
            return None
        normalized_levels = {str(item).strip().upper() for item in low_levels}
        return 1.0 if rating in normalized_levels else 0.0

    def _convertible_small_balance_risk(self, context: SceneAnalysisContext) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        remaining_size = number(context.base_metrics.get("remaining_size"))
        threshold = number(context.convertible_bond_config.get("small_balance_threshold"))
        if remaining_size is None or threshold is None or threshold <= 0:
            return None
        return clamp((threshold - remaining_size) / threshold)

    def _convertible_liquidity_risk(
        self,
        context: SceneAnalysisContext,
        low_turnover: float | None,
        low_volume: float | None,
        small_balance_risk: float | None,
    ) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        return noisy_or([low_turnover, low_volume, small_balance_risk])

    def _evidence(self, tags: dict[str, float], signals: dict) -> list[str]:
        labels = {
            "price_rise": "上涨强度",
            "near_recent_high": "接近近期高位",
            "volume_expand": "放量",
            "high_turnover": "高换手",
            "short_term_emotion": "短线情绪升温",
            "breakout": "突破信号",
            "close_weak": "收盘偏弱",
            "upper_shadow": "上影线",
            "low_turnover": "低换手",
            "low_volume": "低成交",
            "volatility": "波动率",
            "support_distance": "支撑距离",
            "risk_control": "风险控制信号",
            "uncertainty": "不确定性",
            "market_attention_rise": "交易关注度上升",
            "volume_spike": "爆量",
            "large_intraday_move": "日内波动",
            "panic_selling": "恐慌抛售",
            "herding_effect": "交易拥挤",
            "overheated_risk": "过热风险",
            "drawdown_risk": "回撤风险",
            "false_breakout_risk": "假突破风险",
            "liquidity_risk": "流动性风险",
            "chase_high_risk": "追高风险",
            "break_recent_low": "跌破近期低位",
            "downtrend": "下降趋势",
            "redeem_trigger_progress": "强赎触发进度",
            "bond_rating": "转债评级",
            "remaining_size": "剩余规模",
            "convertible_small_balance_risk": "剩余规模过小风险",
        }
        reasons = {
            "chase_high_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_rise", "near_recent_high", "volume_expand", "high_turnover", "short_term_emotion"]}, labels),
                "行情代理信号提高追高风险，chase_high_risk 标签触发",
                "提高追高风险，chase_high_risk 标签触发",
            ),
            "false_breakout_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["breakout", "close_weak", "upper_shadow", "volume_expand"]}, labels),
                "突破相关代理信号提高假突破风险，false_breakout_risk 标签触发",
                "提高假突破风险，false_breakout_risk 标签触发",
            ),
            "liquidity_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["low_turnover", "low_volume"]}, labels),
                "交易活跃度代理信号偏弱，liquidity_risk 标签触发",
                "显示交易活跃度不足，liquidity_risk 标签触发",
            ),
            "drawdown_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["near_recent_high", "volatility", "price_rise", "support_distance"]}, labels),
                "价格位置和波动代理信号提高回撤风险，drawdown_risk 标签触发",
                "提高回撤风险，drawdown_risk 标签触发",
            ),
            "overheated_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_rise", "volume_expand", "high_turnover", "short_term_emotion"]}, labels),
                "行情代理信号提高过热风险，overheated_risk 标签触发",
                "提高过热风险，overheated_risk 标签触发",
            ),
            "risk_control": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["chase_high_risk", "false_breakout_risk", "liquidity_risk", "drawdown_risk", "overheated_risk", "convertible_forced_redeem_risk", "convertible_low_rating_risk", "convertible_small_balance_risk", "convertible_liquidity_risk"]}, labels),
                "多个风险子标签累计后达到风险控制阈值，risk_control 标签触发",
                "累计后达到风险控制阈值，risk_control 标签触发",
            ),
            "position_control": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["risk_control", "volatility", "uncertainty"]}, labels),
                "风险或波动信号提示需要控制仓位，position_control 标签触发",
                "提示需要控制仓位，position_control 标签触发",
            ),
            "wait_confirm": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["breakout", "volume_expand", "short_term_emotion"]}, labels),
                "信号尚未形成充分确认，wait_confirm 标签触发",
                "尚未形成充分确认，wait_confirm 标签触发",
            ),
            "observe_next_day": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["market_attention_rise", "volume_spike", "large_intraday_move"]}, labels),
                "异动代理信号提示需要观察次日延续性，observe_next_day 标签触发",
                "提示需要观察次日延续性，observe_next_day 标签触发",
            ),
            "avoid_emotional_trade": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["short_term_emotion", "panic_selling", "herding_effect", "overheated_risk"]}, labels),
                "情绪或过热代理信号提示避免情绪化交易，avoid_emotional_trade 标签触发",
                "提示避免情绪化交易，avoid_emotional_trade 标签触发",
            ),
            "take_profit_plan": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_rise", "near_recent_high", "overheated_risk", "drawdown_risk"]}, labels),
                "上涨或风险代理信号提示需要预设止盈计划，take_profit_plan 标签触发",
                "提示需要预设止盈计划，take_profit_plan 标签触发",
            ),
            "stop_loss_plan": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["break_recent_low", "downtrend", "panic_selling", "drawdown_risk"]}, labels),
                "下行风险代理信号提示需要预设止损计划，stop_loss_plan 标签触发",
                "提示需要预设止损计划，stop_loss_plan 标签触发",
            ),
            "convertible_forced_redeem_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["redeem_trigger_progress"]}, labels),
                "强赎状态或触发进度提高强赎风险，convertible_forced_redeem_risk 标签触发",
                "提高强赎风险，convertible_forced_redeem_risk 标签触发",
            ),
            "convertible_low_rating_risk": "转债评级落入低评级配置范围，convertible_low_rating_risk 标签触发",
            "convertible_small_balance_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["remaining_size"]}, labels),
                "剩余规模低于配置阈值，convertible_small_balance_risk 标签触发",
                "低于配置阈值，convertible_small_balance_risk 标签触发",
            ),
            "convertible_liquidity_risk": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["low_turnover", "low_volume", "convertible_small_balance_risk"]}, labels),
                "转债交易活跃度或剩余规模代理信号提高流动性风险，convertible_liquidity_risk 标签触发",
                "提高转债流动性风险，convertible_liquidity_risk 标签触发",
            ),
        }
        return build_evidence(tags, reasons)
