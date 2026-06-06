from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.evidence import active_signal_names, build_evidence, joined_signal_reason
from app.scene_analysis.services.module_scoring import active_tags, module_level, module_score, number, weighted_sum
from app.scene_analysis.services.tag_applicability import apply_tag_applicability


class SentimentProcessor:
    MODULE = "sentiment"
    DEFAULT_WEIGHTS = {
        "market_proxy_emotion_weights": {
            "price_rise": 0.35,
            "volume_expand": 0.25,
            "high_turnover": 0.20,
            "market_attention_rise": 0.20,
        },
        "market_proxy_panic_weights": {
            "price_drop": 0.35,
            "volume_expand": 0.25,
            "break_recent_low": 0.25,
            "close_weak": 0.15,
        },
        "market_proxy_weak_weights": {
            "price_drop": 0.25,
            "volume_shrink": 0.20,
            "low_turnover": 0.20,
            "close_weak": 0.20,
            "low_attention": 0.15,
        },
        "market_proxy_herding_weights": {
            "high_turnover": 0.35,
            "volume_expand": 0.30,
            "market_attention_rise": 0.35,
        },
    }

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        tags = {
            "market_attention_rise": number(context.base_metrics.get("market_attention_rise")),
            "short_term_emotion": self._weighted(context, "market_proxy_emotion_weights", {
                "price_rise": number(context.base_metrics.get("price_rise")),
                "volume_expand": number(context.base_metrics.get("volume_expand")),
                "high_turnover": number(context.base_metrics.get("high_turnover")),
                "market_attention_rise": number(context.base_metrics.get("market_attention_rise")),
            }),
            "panic_selling": self._weighted(context, "market_proxy_panic_weights", {
                "price_drop": number(context.base_metrics.get("price_drop")),
                "volume_expand": number(context.base_metrics.get("volume_expand")),
                "break_recent_low": number(context.base_metrics.get("break_recent_low")),
                "close_weak": number(context.base_metrics.get("close_weak")),
            }),
            "weak_sentiment": self._weighted(context, "market_proxy_weak_weights", {
                "price_drop": number(context.base_metrics.get("price_drop")),
                "volume_shrink": number(context.base_metrics.get("volume_shrink")),
                "low_turnover": number(context.base_metrics.get("low_turnover")),
                "close_weak": number(context.base_metrics.get("close_weak")),
                "low_attention": number(context.base_metrics.get("low_attention")),
            }),
            "herding_effect": self._weighted(context, "market_proxy_herding_weights", {
                "high_turnover": number(context.base_metrics.get("high_turnover")),
                "volume_expand": number(context.base_metrics.get("volume_expand")),
                "market_attention_rise": number(context.base_metrics.get("market_attention_rise")),
            }),
            "convertible_stock_linkage": self._convertible_stock_linkage(context),
            "convertible_independent_strength": self._convertible_independent_strength(context),
        }
        tags = apply_tag_applicability(context, active_tags(tags))
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._direction(tags),
            tags=tags,
            evidence=self._evidence(tags, {**context.base_metrics.values, **tags}),
        )

    def _weighted(self, context: SceneAnalysisContext, weight_key: str, signals: dict[str, float | None]) -> float | None:
        weights = context.sentiment_config.get(weight_key)
        return weighted_sum(signals, weights if isinstance(weights, dict) else self.DEFAULT_WEIGHTS.get(weight_key, {}))

    def _direction(self, tags: dict[str, float]) -> str:
        if max(tags.get("short_term_emotion", 0.0), tags.get("herding_effect", 0.0)) >= max(tags.get("panic_selling", 0.0), tags.get("weak_sentiment", 0.0), 0.3):
            return "positive"
        if max(tags.get("panic_selling", 0.0), tags.get("weak_sentiment", 0.0)) >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float], signals: dict) -> list[str]:
        labels = {
            "price_rise": "价格上涨",
            "price_drop": "价格下跌",
            "volume_expand": "放量",
            "volume_shrink": "缩量",
            "high_turnover": "高换手",
            "low_turnover": "低换手",
            "market_attention_rise": "交易关注度上升",
            "break_recent_low": "跌破近期低位",
            "close_weak": "收盘偏弱",
            "low_attention": "交易关注度偏低",
            "underlying_change_pct": "正股涨跌幅",
            "underlying_trend_score": "正股趋势强度",
            "stock_bond_linkage": "转债与正股联动",
            "convertible_premium_expansion": "转股溢价扩张",
        }
        reasons = {
            "market_attention_rise": "基于成交额、换手率和振幅计算的交易关注度较近期均值上升，market_attention_rise 标签触发",
            "short_term_emotion": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_rise", "volume_expand", "high_turnover", "market_attention_rise"]}, labels),
                "行情代理信号显示短线情绪升温，short_term_emotion 标签触发",
                "共同指向短线情绪升温，short_term_emotion 标签触发",
            ),
            "panic_selling": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_drop", "volume_expand", "break_recent_low", "close_weak"]}, labels),
                "行情代理信号显示恐慌抛售压力，panic_selling 标签触发",
                "共同指向恐慌抛售压力，panic_selling 标签触发",
            ),
            "weak_sentiment": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_drop", "volume_shrink", "low_turnover", "close_weak", "low_attention"]}, labels),
                "行情代理信号显示情绪偏弱，weak_sentiment 标签触发",
                "共同指向情绪偏弱，weak_sentiment 标签触发",
            ),
            "herding_effect": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["high_turnover", "volume_expand", "market_attention_rise"]}, labels),
                "行情代理信号显示交易拥挤度提高，herding_effect 标签触发",
                "显示交易拥挤度提高，herding_effect 标签触发",
            ),
            "convertible_stock_linkage": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["underlying_change_pct", "underlying_trend_score", "stock_bond_linkage"]}, labels),
                "正股与转债代理信号显示联动增强，convertible_stock_linkage 标签触发",
                "显示联动增强，convertible_stock_linkage 标签触发",
            ),
            "convertible_independent_strength": joined_signal_reason(
                active_signal_names({key: signals.get(key) for key in ["price_rise", "stock_bond_linkage", "convertible_premium_expansion"]}, labels),
                "转债价格或溢价表现强于正股联动，convertible_independent_strength 标签触发",
                "显示转债独立走强，convertible_independent_strength 标签触发",
            ),
        }
        return build_evidence(tags, reasons)

    def _convertible_stock_linkage(self, context: SceneAnalysisContext) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        linkage = number(context.base_metrics.get("stock_bond_linkage"))
        if linkage is not None:
            return linkage
        underlying_change = number(context.base_metrics.get("underlying_change_pct"))
        bond_change = number(context.base_metrics.get("change_pct"))
        if underlying_change is None or bond_change is None:
            return None
        return 1.0 if underlying_change * bond_change > 0 else 0.0

    def _convertible_independent_strength(self, context: SceneAnalysisContext) -> float | None:
        if not context.is_asset("convertible_bond"):
            return None
        price_rise = number(context.base_metrics.get("price_rise"))
        linkage = number(context.base_metrics.get("stock_bond_linkage"))
        premium_history = context.base_metrics.get("premium_rate_history") or []
        premium_expansion = None
        if len(premium_history) >= 2:
            previous = number(premium_history[-2])
            current = number(premium_history[-1])
            if previous is not None and current is not None:
                premium_expansion = max((current - previous) / max(abs(previous), 1.0), 0.0)
        if price_rise is None and premium_expansion is None:
            return None
        return max((price_rise or 0.0) * (1 - (linkage or 0.0)), premium_expansion or 0.0)
