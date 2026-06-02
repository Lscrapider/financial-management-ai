from __future__ import annotations

from typing import Any

from app.scene_analysis.models import SceneModuleResult


def build_current_scenes_payload(
    *,
    target: dict[str, Any],
    report_type: str | None,
    total_chunks: int,
    module_results: list[SceneModuleResult],
) -> dict[str, Any]:
    return {
        "target": {
            "type": target.get("type"),
            "code": target.get("code"),
            "name": target.get("name"),
        },
        "reportType": report_type,
        "totalChunks": total_chunks,
        "currentScenes": {result.module: _module_payload(result) for result in module_results},
    }


def _module_payload(result: SceneModuleResult) -> dict[str, Any]:
    return {
        "score": result.score,
        "level": result.level,
        "direction": result.direction,
        "tags": result.tags,
        "evidence": result.evidence,
    }
