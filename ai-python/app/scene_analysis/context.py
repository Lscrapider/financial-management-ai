from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.scene_analysis.models import BaseMetrics


@dataclass(frozen=True)
class SceneAnalysisContext:
    message: dict[str, Any]
    base_metrics: BaseMetrics
    target: dict[str, Any]
    config: dict[str, Any]
    asset_config: dict[str, Any]
    price_config: dict[str, Any]
    volume_config: dict[str, Any]
    sentiment_config: dict[str, Any]
    risk_strategy_config: dict[str, Any]
    industry_data: dict[str, Any]

    @classmethod
    def from_message(cls, message: dict[str, Any], base_metrics: BaseMetrics) -> SceneAnalysisContext:
        target = _dict(message.get("target"))
        config = _dict(_dict(message.get("config")).get("parameters"))
        return cls(
            message=message,
            base_metrics=base_metrics,
            target=target,
            config=config,
            asset_config=_dict(config.get("asset_config")),
            price_config=_dict(config.get("price_config")),
            volume_config=_dict(config.get("volume_config")),
            sentiment_config=_dict(config.get("sentiment_config")),
            risk_strategy_config=_dict(config.get("risk_strategy_config")),
            industry_data=_dict(message.get("industryData")),
        )


def _dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}
