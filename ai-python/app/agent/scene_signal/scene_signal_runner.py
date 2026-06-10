from __future__ import annotations

from collections.abc import Callable
from typing import Any

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import BaseMetrics, SceneModuleResult
from app.scene_analysis.services.asset_processor import AssetProcessor
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator
from app.scene_analysis.services.current_scene_result import _scene_output_key
from app.scene_analysis.services.price_processor import PriceProcessor
from app.scene_analysis.services.risk_strategy_processor import RiskStrategyProcessor
from app.scene_analysis.services.sentiment_processor import SentimentProcessor
from app.scene_analysis.services.trend_processor import TrendProcessor
from app.scene_analysis.services.valuation_processor import ValuationProcessor
from app.scene_analysis.services.volume_processor import VolumeProcessor

SCENE_DEPENDENCIES: dict[str, list[str]] = {
    "asset": [],
    "price": [],
    "volume": [],
    "trend": [],
    "sentiment": [],
    "valuation": ["trend"],
    "risk_strategy": ["trend", "valuation", "sentiment"],
}


class SceneSignalRunner:
    def __init__(
        self,
        base_metrics_calculator: BaseMetricsCalculator | None = None,
        context_factory: Callable[[dict[str, Any], BaseMetrics], Any] | None = None,
        asset_processor: AssetProcessor | None = None,
        price_processor: PriceProcessor | None = None,
        volume_processor: VolumeProcessor | None = None,
        trend_processor: TrendProcessor | None = None,
        valuation_processor: ValuationProcessor | None = None,
        sentiment_processor: SentimentProcessor | None = None,
        risk_strategy_processor: RiskStrategyProcessor | None = None,
    ) -> None:
        self._base_metrics_calculator = base_metrics_calculator or BaseMetricsCalculator()
        self._context_factory = context_factory or SceneAnalysisContext.from_message
        self._asset_processor = asset_processor or AssetProcessor()
        self._price_processor = price_processor or PriceProcessor()
        self._volume_processor = volume_processor or VolumeProcessor()
        self._trend_processor = trend_processor or TrendProcessor()
        self._valuation_processor = valuation_processor or ValuationProcessor()
        self._sentiment_processor = sentiment_processor or SentimentProcessor()
        self._risk_strategy_processor = risk_strategy_processor or RiskStrategyProcessor()

    def run(self, message: dict[str, Any], requested_scenes: list[str] | None) -> dict[str, dict[str, Any]]:
        requested = self._normalize_requested_scenes(requested_scenes)
        base_metrics = self._base_metrics_calculator.calculate(message)
        context = self._context_factory(message, base_metrics)
        results: dict[str, SceneModuleResult] = {}

        for scene in requested:
            self._ensure_scene(scene, context, results)

        return {
            _scene_output_key(scene): results[scene].to_dict()
            for scene in requested
            if scene in results
        }

    def _ensure_scene(
        self,
        scene: str,
        context: Any,
        results: dict[str, SceneModuleResult],
    ) -> SceneModuleResult:
        if scene in results:
            return results[scene]
        for dependency in SCENE_DEPENDENCIES[scene]:
            self._ensure_scene(dependency, context, results)
        result = self._calculate_scene(scene, context, results)
        results[scene] = result
        return result

    def _calculate_scene(
        self,
        scene: str,
        context: Any,
        results: dict[str, SceneModuleResult],
    ) -> SceneModuleResult:
        if scene == "asset":
            return self._asset_processor.process(context)
        if scene == "price":
            return self._price_processor.process(context)
        if scene == "volume":
            return self._volume_processor.process(context)
        if scene == "trend":
            return self._trend_processor.process(context)
        if scene == "valuation":
            return self._valuation_processor.process(context, results["trend"].tags)
        if scene == "sentiment":
            return self._sentiment_processor.process(context)
        if scene == "risk_strategy":
            return self._risk_strategy_processor.process(
                context,
                results["trend"].tags,
                results["valuation"].tags,
                results["sentiment"].tags,
            )
        raise ValueError(f"unsupported scene: {scene}")

    def _normalize_requested_scenes(self, scenes: list[str] | None) -> list[str]:
        raw = scenes or ["asset", "price", "volume", "trend", "valuation", "sentiment", "risk_strategy"]
        normalized: list[str] = []
        for scene in raw:
            key = str(scene or "").strip()
            if not key:
                continue
            if key == "riskStrategy":
                key = "risk_strategy"
            if key not in SCENE_DEPENDENCIES:
                raise ValueError(f"unsupported scene: {key}")
            if key not in normalized:
                normalized.append(key)
        return normalized
