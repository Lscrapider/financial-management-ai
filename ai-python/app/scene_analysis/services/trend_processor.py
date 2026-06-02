from __future__ import annotations

from typing import Any

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import BaseMetrics, SceneModuleResult
from app.scene_analysis.services.evidence import build_evidence
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number, weighted_sum


class TrendProcessor:
    MODULE = "trend"

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        metrics = context.base_metrics
        uptrend = self._uptrend(metrics)
        downtrend = self._downtrend(metrics)
        range_bound = self._range_bound(metrics)
        tags = {
            "uptrend": uptrend,
            "downtrend": downtrend,
            "range_bound": range_bound,
            "rebound": self._rebound(context, downtrend),
            "trend_reversal": self._trend_reversal(metrics, uptrend, downtrend),
            "breakout_from_range": self._breakout_from_range(context, range_bound),
            "failed_breakout": self._failed_breakout(metrics),
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

    def _uptrend(self, metrics: BaseMetrics) -> float:
        current_price = number(metrics.get("current_price") or metrics.get("latest_price"))
        ma5 = number(metrics.get("ma5"))
        ma10 = number(metrics.get("ma10"))
        ma20 = number(metrics.get("ma20"))
        if None in (ma5, ma10, ma20):
            return 0.0
        score = 0.8 if ma5 > ma10 > ma20 else 0.0
        if current_price is not None and ma5 is not None and current_price > ma5:
            score += 0.1
        if current_price is not None and ma20 is not None and current_price > ma20:
            score += 0.1
        return clamp(score)

    def _downtrend(self, metrics: BaseMetrics) -> float:
        current_price = number(metrics.get("current_price") or metrics.get("latest_price"))
        ma5 = number(metrics.get("ma5"))
        ma10 = number(metrics.get("ma10"))
        ma20 = number(metrics.get("ma20"))
        if None in (ma5, ma10, ma20):
            return 0.0
        score = 0.8 if ma5 < ma10 < ma20 else 0.0
        if current_price is not None and ma5 is not None and current_price < ma5:
            score += 0.1
        if current_price is not None and ma20 is not None and current_price < ma20:
            score += 0.1
        return clamp(score)

    def _range_bound(self, metrics: BaseMetrics) -> float | None:
        range_pct_20d = number(metrics.get("range_pct_20d"))
        if range_pct_20d is None:
            return None
        return clamp((0.10 - range_pct_20d) / 0.10)

    def _rebound(self, context: SceneAnalysisContext, downtrend: float) -> float | None:
        current_price = number(context.base_metrics.get("current_price") or context.base_metrics.get("latest_price"))
        recent_low = number(context.base_metrics.get("recent_low_20d"))
        price_rise = number(context.base_metrics.get("price_rise"))
        threshold = number(context.trend_config.get("rebound_threshold"))
        if None in (current_price, recent_low, price_rise) or not threshold or recent_low == 0:
            return None
        return clamp(downtrend * price_rise * clamp((current_price - recent_low) / recent_low / threshold))

    def _trend_reversal(self, metrics: BaseMetrics, uptrend: float, downtrend: float) -> float:
        current_price = number(metrics.get("current_price") or metrics.get("latest_price"))
        ma5 = number(metrics.get("ma5"))
        ma20 = number(metrics.get("ma20"))
        price_return_5d = number(metrics.get("price_return_5d"))
        if None in (current_price, ma5, ma20, price_return_5d):
            return 0.0
        down_to_up = (1 - downtrend) * clamp((current_price - ma20) / ma20 / 0.05) if ma20 and current_price > ma20 and price_return_5d > 0 else 0.0
        up_to_down = (1 - uptrend) * clamp((ma20 - current_price) / ma20 / 0.05) if ma20 and current_price < ma20 and price_return_5d < 0 else 0.0
        return clamp(max(down_to_up, up_to_down))

    def _breakout_from_range(self, context: SceneAnalysisContext, range_bound: float | None) -> float | None:
        breakout = number(context.base_metrics.get("breakout"))
        volume_expand = number(context.base_metrics.get("volume_expand"))
        weights = context.trend_config.get("breakout_from_range_confirm_weights")
        confirm_score = weighted_sum({"base_confirm": 1.0, "volume_expand": volume_expand}, weights if isinstance(weights, dict) else {})
        if range_bound is None or breakout is None or confirm_score is None:
            return None
        return clamp(range_bound * breakout * confirm_score)

    def _failed_breakout(self, metrics: BaseMetrics) -> float | None:
        breakout = number(metrics.get("breakout"))
        close_weak = number(metrics.get("close_weak"))
        upper_shadow = number(metrics.get("upper_shadow"))
        if None in (breakout, close_weak, upper_shadow):
            return None
        return clamp(breakout * close_weak * upper_shadow)

    def _direction(self, tags: dict[str, float]) -> str:
        if tags.get("uptrend", 0.0) > tags.get("downtrend", 0.0) and tags.get("uptrend", 0.0) >= 0.3:
            return "positive"
        if tags.get("downtrend", 0.0) >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "uptrend": "短中期均线呈多头排列且价格位于均线之上，uptrend 标签触发",
            "downtrend": "短中期均线呈空头排列且价格位于均线之下，downtrend 标签触发",
            "range_bound": "近 20 日价格区间收敛，range_bound 标签触发",
            "rebound": "下降趋势中价格从近期低位回升，rebound 标签触发",
            "trend_reversal": "价格与均线关系出现趋势切换信号，trend_reversal 标签触发",
            "breakout_from_range": "区间震荡后价格向上突破并获得成交确认，breakout_from_range 标签触发",
            "failed_breakout": "突破后收盘偏弱且上影线明显，failed_breakout 标签触发",
        }
        return build_evidence(tags, reasons)
