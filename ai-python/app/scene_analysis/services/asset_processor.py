from __future__ import annotations

from typing import Any

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.module_scoring import active_tags, module_level, module_score, number


class AssetProcessor:
    MODULE = "asset"

    def process(self, context: SceneAnalysisContext) -> SceneModuleResult:
        asset_type = self._asset_type(context.config, context.target)
        configured_asset_type = self._normalize_asset_type(context.config.get("asset_type"))
        industry_name = str(context.industry_data.get("industryName") or "")
        current_price = number(context.base_metrics.get("current_price") or context.base_metrics.get("latest_price"))
        low_price_threshold = number(context.asset_config.get("low_price_threshold"))

        tags: dict[str, float] = {}
        evidence: list[str] = []

        self._add_asset_type_tags(tags, evidence, asset_type)
        if asset_type == "stock" and (configured_asset_type == "bank_stock" or self._is_bank_industry(industry_name)):
            tags["bank_stock"] = 1.0
            evidence.append("行业或用户配置识别为银行股，bank_stock 标签命中")
        if asset_type == "stock" and current_price is not None and low_price_threshold is not None:
            if current_price <= low_price_threshold:
                tags["low_price_stock"] = 1.0
                evidence.append("当前价格不高于低价股阈值，low_price_stock 标签命中")
            else:
                tags["low_price_stock"] = 0.0

        if not tags:
            tags["general"] = 1.0
            evidence.append("未识别到股票、指数、可转债或基金等更具体资产类型，使用 general 标签")

        tags = active_tags(tags)
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction="neutral",
            tags=tags,
            evidence=evidence,
        )

    def _add_asset_type_tags(self, tags: dict[str, float], evidence: list[str], asset_type: str | None) -> None:
        if asset_type == "stock":
            tags["stock"] = 1.0
            evidence.append("标的类型识别为股票，stock 标签命中")
            return
        if asset_type == "index":
            tags["index"] = 1.0
            evidence.append("标的类型识别为指数，index 标签命中")
            return
        if asset_type == "convertible_bond":
            tags["convertible_bond"] = 1.0
            evidence.append("标的类型识别为可转债，convertible_bond 标签命中")
            return
        if asset_type in {"fund", "etf"}:
            tags["fund"] = 1.0
            evidence.append("标的类型识别为基金或 ETF，fund 标签命中")

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
