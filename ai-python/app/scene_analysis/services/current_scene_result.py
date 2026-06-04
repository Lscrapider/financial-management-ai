from __future__ import annotations

from typing import Any

from app.scene_analysis.models import SceneModuleResult

SCENE_OUTPUT_KEYS = {
    "risk_strategy": "riskStrategy",
}


def build_current_scenes_payload(
    *,
    target: dict[str, Any],
    report_type: str | None,
    total_chunks: int,
    market_context: dict[str, Any] | None = None,
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
        "marketContext": market_context or {},
        "currentScenes": {_scene_output_key(result.module): _module_payload(result) for result in module_results},
    }


def _scene_output_key(module: str) -> str:
    return SCENE_OUTPUT_KEYS.get(module, module)


def _module_payload(result: SceneModuleResult) -> dict[str, Any]:
    payload = {
        "score": result.score,
        "level": result.level,
        "direction": result.direction,
        "tags": result.tags,
        "evidence": result.evidence,
        "queryText": result.query_text(),
    }
    payload.update(result.extra)
    return payload
