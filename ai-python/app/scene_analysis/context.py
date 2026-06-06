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
    convertible_bond_config: dict[str, Any]
    fund_config: dict[str, Any]
    industry_data: dict[str, Any]
    asset_specific_data: dict[str, Any]
    convertible_bond_data: dict[str, Any]
    fund_data: dict[str, Any]

    @classmethod
    def from_message(cls, message: dict[str, Any], base_metrics: BaseMetrics) -> SceneAnalysisContext:
        target = _dict(message.get("target"))
        config = _dict(_dict(message.get("config")).get("parameters"))
        asset_specific_data = _dict(message.get("assetSpecificData"))
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
            convertible_bond_config=_dict(config.get("convertible_bond_config")),
            fund_config=_dict(config.get("fund_config")),
            industry_data=_dict(message.get("industryData")),
            asset_specific_data=asset_specific_data,
            convertible_bond_data=_dict(asset_specific_data.get("convertibleBond")),
            fund_data=_dict(asset_specific_data.get("fund")),
        )

    @property
    def asset_type(self) -> str | None:
        configured = _normalize_asset_type(self.config.get("asset_type"))
        return configured or _normalize_asset_type(self.target.get("type"))

    def is_asset(self, *asset_types: str) -> bool:
        asset_type = self.asset_type
        return asset_type is not None and asset_type in asset_types


def _dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _normalize_asset_type(value: Any) -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    normalized = value.strip().lower()
    return {
        "stock": "stock",
        "bank_stock": "stock",
        "股票": "stock",
        "index": "index",
        "指数": "index",
        "convertible_bond": "convertible_bond",
        "bond": "convertible_bond",
        "convertible_bond_cn": "convertible_bond",
        "可转债": "convertible_bond",
        "fund": "fund",
        "etf": "fund",
        "lof": "fund",
        "基金": "fund",
    }.get(normalized, normalized)
