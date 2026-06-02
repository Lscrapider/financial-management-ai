from __future__ import annotations

from typing import Any

from app.scene_analysis.models import BaseMetrics, SceneModuleResult


class AssetProcessor:
    MODULE = "asset"

    def process(self, message: dict[str, Any], base_metrics: BaseMetrics) -> SceneModuleResult:
        target = self._dict(message.get("target"))
        config = self._dict(self._dict(message.get("config")).get("parameters"))
        industry_data = self._dict(message.get("industryData"))
        asset_config = self._dict(config.get("asset_config"))

        asset_type = self._asset_type(config, target)
        configured_asset_type = self._normalize_asset_type(config.get("asset_type"))
        industry_name = str(industry_data.get("industryName") or "")
        current_price = self._number(base_metrics.get("current_price") or base_metrics.get("latest_price"))
        low_price_threshold = self._number(asset_config.get("low_price_threshold"))

        tags: dict[str, float] = {}
        evidence: list[str] = []

        self._add_asset_type_tags(tags, evidence, asset_type)
        if asset_type == "stock" and (configured_asset_type == "bank_stock" or self._is_bank_industry(industry_name)):
            tags["bank_stock"] = 1.0
            evidence.append("标的为银行股")
        if asset_type == "stock" and current_price is not None and low_price_threshold is not None:
            if current_price <= low_price_threshold:
                tags["low_price_stock"] = 1.0
                evidence.append("当前价格处于低价股区间")
            else:
                tags["low_price_stock"] = 0.0

        if not tags:
            tags["general"] = 1.0
            evidence.append("未识别到更具体的资产类型")

        score = max(tags.values()) if tags else 0.0
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=self._level(score),
            direction="neutral",
            tags=tags,
            evidence=evidence,
        )

    def _add_asset_type_tags(self, tags: dict[str, float], evidence: list[str], asset_type: str | None) -> None:
        if asset_type == "stock":
            tags["stock"] = 1.0
            evidence.append("标的类型为股票")
            return
        if asset_type == "index":
            tags["index"] = 1.0
            evidence.append("标的类型为指数")
            return
        if asset_type == "convertible_bond":
            tags["convertible_bond"] = 1.0
            evidence.append("标的类型为可转债")
            return
        if asset_type in {"fund", "etf"}:
            tags["fund"] = 1.0
            evidence.append("标的类型为基金")

    def _asset_type(self, config: dict[str, Any], target: dict[str, Any]) -> str | None:
        configured = self._normalize_asset_type(config.get("asset_type"))
        if configured:
            if configured == "bank_stock":
                return "stock"
            return configured
        return self._normalize_asset_type(target.get("type"))

    def _normalize_asset_type(self, value: Any) -> str | None:
        if not isinstance(value, str) or not value.strip():
            return None
        normalized = value.strip().lower()
        return {
            "stock": "stock",
            "bank_stock": "bank_stock",
            "股票": "stock",
            "index": "index",
            "指数": "index",
            "convertible_bond": "convertible_bond",
            "bond": "convertible_bond",
            "convertible_bond_cn": "convertible_bond",
            "可转债": "convertible_bond",
            "fund": "fund",
            "etf": "etf",
            "基金": "fund",
        }.get(normalized, normalized)

    def _is_bank_industry(self, industry_name: str) -> bool:
        normalized = industry_name.strip().lower()
        return "银行" in normalized or "bank" in normalized

    def _level(self, score: float) -> str:
        if score >= 0.7:
            return "high"
        if score >= 0.3:
            return "medium"
        return "low"

    def _dict(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _number(self, value: Any) -> float | None:
        if value is None or value == "":
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None
