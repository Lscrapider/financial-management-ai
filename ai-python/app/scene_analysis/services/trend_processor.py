from __future__ import annotations

from typing import Any

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult, TAG_NAMES
from app.scene_analysis.services.module_scoring import clamp, module_level
from app.scene_analysis.services.tag_applicability import apply_tag_applicability
from app.scene_analysis.services.trend_kline_analysis import NEGATIVE_TAGS, POSITIVE_TAGS, TrendKlineAnalyzer


class TrendProcessor:
    MODULE = "trend"
    PERIOD_WEIGHTS = {
        "daily": 0.2,
        "weekly": 0.5,
        "monthly": 0.3,
    }
    PERIOD_LABELS = {
        "daily": "日线",
        "weekly": "周线",
        "monthly": "月线",
    }

    def __init__(self, analyzer: TrendKlineAnalyzer | None = None) -> None:
        self._analyzer = analyzer or TrendKlineAnalyzer()

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        period_trends = {
            "daily": self._analyzer.analyze("daily", self._list(context.message.get("dailyKlines"))),
            "weekly": self._analyzer.analyze("weekly", self._list(context.message.get("weeklyKlines"))),
            "monthly": self._analyzer.analyze("monthly", self._list(context.message.get("monthlyKlines"))),
        }
        tags = apply_tag_applicability(context, self._merge_tags(period_trends))
        score = self._weighted_score(period_trends)
        direction = self._direction(tags, period_trends)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=direction,
            tags=tags,
            evidence=[],
            extra={"periodTrends": self._period_trends_payload(period_trends)},
            query_text_override=self._query_text(tags, period_trends),
        )

    def _merge_tags(self, period_trends: dict[str, dict[str, Any]]) -> dict[str, float]:
        merged: dict[str, float] = {}
        for trend in period_trends.values():
            for tag, score in self._dict(trend.get("tags")).items():
                numeric = score if isinstance(score, (int, float)) and not isinstance(score, bool) else None
                if numeric is None:
                    continue
                merged[tag] = max(merged.get(tag, 0.0), clamp(float(numeric)))
        return dict(sorted(merged.items(), key=lambda item: item[1], reverse=True))

    def _weighted_score(self, period_trends: dict[str, dict[str, Any]]) -> float:
        return clamp(sum(
            self._score(period_trends.get(period)) * weight
            for period, weight in self.PERIOD_WEIGHTS.items()
        ))

    def _direction(self, tags: dict[str, float], period_trends: dict[str, dict[str, Any]]) -> str:
        positive = max((tags.get(tag, 0.0) for tag in POSITIVE_TAGS), default=0.0)
        negative = max((tags.get(tag, 0.0) for tag in NEGATIVE_TAGS), default=0.0)
        continuation = tags.get("continuation", 0.0)
        if continuation >= 0.3:
            weighted_positive = sum(
                self.PERIOD_WEIGHTS[period] * self._score(trend)
                for period, trend in period_trends.items()
                if trend.get("direction") == "positive"
            )
            weighted_negative = sum(
                self.PERIOD_WEIGHTS[period] * self._score(trend)
                for period, trend in period_trends.items()
                if trend.get("direction") == "negative"
            )
            if weighted_negative > weighted_positive:
                negative = max(negative, continuation)
            else:
                positive = max(positive, continuation)
        if positive > negative and positive >= 0.3:
            return "positive"
        if negative >= 0.3:
            return "negative"
        return "neutral"

    def _query_text(self, tags: dict[str, float], period_trends: dict[str, dict[str, Any]]) -> str:
        active_tag_names = [
            TAG_NAMES.get(tag, tag)
            for tag, score in tags.items()
            if score >= 0.3
        ]
        parts = ["趋势分析"]
        if active_tag_names:
            parts[0] = f"{parts[0]}，{'、'.join(active_tag_names)}"
        summaries = [
            self._period_summary(period, trend)
            for period, trend in period_trends.items()
            if self._score(trend) >= 0.3
        ]
        if summaries:
            return f"{parts[0]}。{'；'.join(summaries)}。"
        return f"{parts[0]}。"

    def _period_summary(self, period: str, trend: dict[str, Any]) -> str:
        tags = self._dict(trend.get("tags"))
        tag_names = [
            TAG_NAMES.get(tag, tag)
            for tag, score in tags.items()
            if isinstance(score, (int, float)) and not isinstance(score, bool) and score >= 0.3
        ]
        label = self.PERIOD_LABELS.get(period, period)
        direction = {
            "positive": "偏强",
            "negative": "偏弱",
            "neutral": "中性",
        }.get(str(trend.get("direction")), "中性")
        if tag_names:
            return f"{label}{direction}，主要标签为{'、'.join(tag_names[:4])}"
        return f"{label}{direction}"

    def _score(self, trend: dict[str, Any] | None) -> float:
        if not trend:
            return 0.0
        value = trend.get("score")
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            return 0.0
        return clamp(float(value))

    def _period_trends_payload(self, period_trends: dict[str, dict[str, Any]]) -> dict[str, dict[str, Any]]:
        return {
            period: {
                "score": trend.get("score", 0.0),
                "level": trend.get("level", "low"),
                "direction": trend.get("direction", "neutral"),
                "tags": trend.get("tags", {}),
                "evidence": trend.get("evidence", []),
            }
            for period, trend in period_trends.items()
        }

    def _dict(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _list(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        return [row for row in value if isinstance(row, dict)]
