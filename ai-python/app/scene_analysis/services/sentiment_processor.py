from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.module_scoring import active_tags, module_level, module_score, number, weighted_sum


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
        }
        tags = active_tags(tags)
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._direction(tags),
            tags=tags,
            evidence=self._evidence(tags),
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

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        messages = {
            "market_attention_rise": "交易关注度代理信号升温",
            "short_term_emotion": "上涨、放量、换手或交易关注度支持短线情绪升温",
            "panic_selling": "下跌、放量、跌破前低或收盘弱支持恐慌抛售代理信号",
            "weak_sentiment": "价格、成交、换手或关注度偏弱",
            "herding_effect": "高换手、放量或交易关注度升温支持交易拥挤代理信号",
        }
        return [message for key, message in messages.items() if tags.get(key, 0.0) >= 0.3]
