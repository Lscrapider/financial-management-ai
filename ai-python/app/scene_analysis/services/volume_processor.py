from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.evidence import build_evidence
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number, score_value
from app.scene_analysis.services.tag_applicability import apply_tag_applicability


class VolumeProcessor:
    MODULE = "volume"

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        metrics = context.base_metrics
        tags = {
            "volume_expand": score_value(metrics.get("volume_expand")),
            "volume_shrink": score_value(metrics.get("volume_shrink")),
            "high_turnover": score_value(metrics.get("high_turnover")),
            "low_turnover": score_value(metrics.get("low_turnover")),
            "volume_price_confirm": self._volume_price_confirm(context),
            "volume_price_divergence": self._volume_price_divergence(context),
            "volume_spike": score_value(metrics.get("volume_spike")),
            "volume_dry_up": self._volume_dry_up(context),
        }
        tags = apply_tag_applicability(context, active_tags(tags))
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._direction(tags),
            tags=tags,
            evidence=self._evidence(tags),
        )

    def _volume_price_confirm(self, context: SceneAnalysisContext) -> float | None:
        price_rise = number(context.base_metrics.get("price_rise"))
        price_drop = number(context.base_metrics.get("price_drop"))
        volume_expand = number(context.base_metrics.get("volume_expand"))
        volume_shrink = number(context.base_metrics.get("volume_shrink"))
        if None in (price_rise, price_drop, volume_expand, volume_shrink):
            return None
        return clamp(max(price_rise * volume_expand, price_drop * volume_shrink))

    def _volume_price_divergence(self, context: SceneAnalysisContext) -> float | None:
        price_rise = number(context.base_metrics.get("price_rise"))
        volume_shrink = number(context.base_metrics.get("volume_shrink"))
        volume_expand = number(context.base_metrics.get("volume_expand"))
        close_weak = number(context.base_metrics.get("close_weak"))
        if None in (price_rise, volume_shrink, volume_expand, close_weak):
            return None
        return clamp(max(price_rise * volume_shrink, volume_expand * close_weak))

    def _volume_dry_up(self, context: SceneAnalysisContext) -> float | None:
        current_ratio = number(context.base_metrics.get("volume_ratio_20d"))
        history = context.base_metrics.get("volume_ratio_20d_history")
        if current_ratio is None or not isinstance(history, list) or not history:
            return None
        valid_history = [item for item in (number(value) for value in history) if item is not None]
        if not valid_history:
            return None
        rank = sum(1 for value in valid_history if value <= current_ratio) / len(valid_history)
        return clamp(1 - rank)

    def _direction(self, tags: dict[str, float]) -> str:
        positive = max(tags.get("volume_expand", 0.0), tags.get("high_turnover", 0.0), tags.get("volume_price_confirm", 0.0))
        negative = max(tags.get("volume_shrink", 0.0), tags.get("low_turnover", 0.0), tags.get("volume_dry_up", 0.0))
        if positive > negative and positive >= 0.3:
            return "positive"
        if negative > positive and negative >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "volume_expand": "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发",
            "volume_shrink": "当前成交量在 60 日历史分布中偏低，volume_shrink 标签触发",
            "high_turnover": "当前换手率处于历史分布较高位置，high_turnover 标签触发",
            "low_turnover": "当前换手率处于历史分布较低位置，low_turnover 标签触发",
            "volume_price_confirm": "价格方向与成交量变化相互确认，volume_price_confirm 标签触发",
            "volume_price_divergence": "价格方向与成交量或收盘强弱出现背离，volume_price_divergence 标签触发",
            "volume_spike": "当前成交量相对 60 日稳健分布出现异常放大，volume_spike 标签触发",
            "volume_dry_up": "当前量比在历史量比分布中偏低，volume_dry_up 标签触发",
        }
        return build_evidence(tags, reasons)
