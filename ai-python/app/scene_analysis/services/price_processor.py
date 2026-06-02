from __future__ import annotations

from typing import Any

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import BaseMetrics, SceneModuleResult
from app.scene_analysis.services.evidence import build_evidence
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number, score_value


class PriceProcessor:
    MODULE = "price"

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        base_metrics = context.base_metrics
        tags = {
            "price_rise": score_value(base_metrics.get("price_rise")),
            "price_drop": score_value(base_metrics.get("price_drop")),
            "sideways": self._sideways(base_metrics),
            "near_recent_high": score_value(base_metrics.get("near_recent_high")),
            "near_recent_low": score_value(base_metrics.get("near_recent_low")),
            "breakout": score_value(base_metrics.get("breakout")),
            "break_recent_low": score_value(base_metrics.get("break_recent_low")),
            "pullback": self._pullback(base_metrics, context.price_config),
            "gap_up": self._gap_up(base_metrics, context.price_config),
            "gap_down": self._gap_down(base_metrics, context.price_config),
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

    def _sideways(self, base_metrics: BaseMetrics) -> float | None:
        range_pct_20d = number(base_metrics.get("range_pct_20d"))
        if range_pct_20d is None:
            return None
        return clamp((0.08 - range_pct_20d) / 0.08)

    def _pullback(self, base_metrics: BaseMetrics, price_config: dict[str, Any]) -> float | None:
        current_price = number(base_metrics.get("current_price") or base_metrics.get("latest_price"))
        recent_high = number(base_metrics.get("recent_high_20d"))
        threshold = number(price_config.get("pullback_threshold"))
        if current_price is None or recent_high is None or not threshold:
            return None
        if not self._is_uptrend(base_metrics) or current_price >= recent_high or recent_high == 0:
            return 0.0
        return clamp((recent_high - current_price) / recent_high / threshold)

    def _gap_up(self, base_metrics: BaseMetrics, price_config: dict[str, Any]) -> float | None:
        open_price = number(base_metrics.get("open_price"))
        previous_high = number(base_metrics.get("prev_high_20d"))
        previous_close = number(base_metrics.get("previous_close_price"))
        threshold = number(price_config.get("gap_threshold"))
        if open_price is None or previous_high is None or not previous_close or not threshold:
            return None
        if open_price <= previous_high:
            return 0.0
        return clamp((open_price - previous_high) / previous_close / threshold)

    def _gap_down(self, base_metrics: BaseMetrics, price_config: dict[str, Any]) -> float | None:
        open_price = number(base_metrics.get("open_price"))
        previous_low = number(base_metrics.get("prev_low_20d"))
        previous_close = number(base_metrics.get("previous_close_price"))
        threshold = number(price_config.get("gap_threshold"))
        if open_price is None or previous_low is None or not previous_close or not threshold:
            return None
        if open_price >= previous_low:
            return 0.0
        return clamp((previous_low - open_price) / previous_close / threshold)

    def _is_uptrend(self, base_metrics: BaseMetrics) -> bool:
        ma5 = number(base_metrics.get("ma5"))
        ma10 = number(base_metrics.get("ma10"))
        ma20 = number(base_metrics.get("ma20"))
        if ma5 is not None and ma10 is not None and ma20 is not None:
            return ma5 > ma10 > ma20
        price_return_20d = number(base_metrics.get("price_return_20d"))
        return price_return_20d is not None and price_return_20d > 0

    def _direction(self, tags: dict[str, float]) -> str:
        positive = max(tags.get("price_rise", 0.0), tags.get("breakout", 0.0), tags.get("near_recent_high", 0.0))
        negative = max(tags.get("price_drop", 0.0), tags.get("break_recent_low", 0.0), tags.get("close_weak", 0.0))
        if positive > negative and positive >= 0.3:
            return "positive"
        if negative > positive and negative >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "price_rise": "当日涨幅超过上涨强度阈值，price_rise 标签触发",
            "price_drop": "当日跌幅超过下跌强度阈值，price_drop 标签触发",
            "sideways": "近 20 日价格振幅较窄，sideways 标签触发",
            "near_recent_high": "当前价格处于近 20 日区间高位，near_recent_high 标签触发",
            "near_recent_low": "当前价格处于近 20 日区间低位，near_recent_low 标签触发",
            "breakout": "当前价格突破近 20 日前高，breakout 标签触发",
            "break_recent_low": "当前价格跌破近 20 日前低，break_recent_low 标签触发",
            "pullback": "上升趋势中价格从近期高位回落，pullback 标签触发",
            "gap_up": "开盘价高于前期高点并形成向上跳空，gap_up 标签触发",
            "gap_down": "开盘价低于前期低点并形成向下跳空，gap_down 标签触发",
        }
        return build_evidence(tags, reasons)
