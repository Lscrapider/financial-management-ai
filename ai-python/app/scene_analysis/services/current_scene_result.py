from __future__ import annotations

from typing import Any

from app.scene_analysis.models import BaseMetrics, SceneModuleResult


def build_current_scenes_payload(
    *,
    task_no: str,
    target: dict[str, Any],
    report_type: str | None,
    base_metrics: BaseMetrics,
    module_results: list[SceneModuleResult],
) -> dict[str, Any]:
    return {
        "taskNo": task_no,
        "target": {
            "type": target.get("type"),
            "code": target.get("code"),
            "name": target.get("name"),
        },
        "reportType": report_type,
        "currentScenes": {result.module: _module_payload(result) for result in module_results},
        "baseMetrics": _base_metrics_payload(base_metrics),
    }


def _module_payload(result: SceneModuleResult) -> dict[str, Any]:
    return {
        "score": result.score,
        "level": result.level,
        "direction": result.direction,
        "tags": result.tags,
        "evidence": result.evidence,
    }


def _base_metrics_payload(base_metrics: BaseMetrics) -> dict[str, Any]:
    return {
        "metricCount": len(base_metrics.values),
        "missing": base_metrics.missing,
    }
