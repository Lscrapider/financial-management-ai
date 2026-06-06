from pathlib import Path
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator
from app.scene_analysis.services.price_processor import PriceProcessor
from app.scene_analysis.services.valuation_processor import ValuationProcessor


def test_convertible_bond_uses_asset_specific_tags() -> None:
    message = {
        "target": {"type": "CONVERTIBLE_BOND", "code": "123456", "name": "示例转债"},
        "config": {
            "parameters": {
                "asset_type": "convertible_bond",
                "price_config": {
                    "price_rise_center": 2.0,
                    "price_rise_scale": 1.2,
                    "price_drop_center": 2.0,
                    "price_drop_scale": 1.2,
                    "price_move_center": 2.0,
                    "price_move_scale": 1.2,
                    "pullback_threshold": 0.08,
                    "gap_threshold": 0.03,
                },
                "volume_config": {
                    "volume_expand_center": 1.0,
                    "volume_expand_scale": 0.8,
                    "volume_spike_center": 1.8,
                    "volume_spike_scale": 0.7,
                },
                "convertible_bond_config": {
                    "premium_low_threshold": 10.0,
                    "premium_high_threshold": 30.0,
                    "high_price_threshold": 120.0,
                    "low_price_threshold": 100.0,
                    "high_ytm_threshold": 3.0,
                    "low_ytm_threshold": 0.0,
                },
            }
        },
        "marketData": {
            "latestPrice": 132.0,
            "previousClosePrice": 128.0,
            "changePercent": 3.1,
        },
        "assetSpecificData": {
            "convertibleBond": {
                "bondPrice": 132.0,
                "premiumRate": 45.0,
                "premiumRateHistory": [38.0, 45.0],
                "conversionValue": 105.0,
                "conversionValueHistory": [90.0, 100.0, 105.0],
                "ytm": -1.2,
            }
        },
        "dailyKlines": [],
    }

    base_metrics = BaseMetricsCalculator().calculate(message)
    context = SceneAnalysisContext.from_message(message, base_metrics)
    price_result = PriceProcessor().process(context)
    valuation_result = ValuationProcessor().process(context)

    assert "convertible_high_price_risk" in price_result.tags
    assert "convertible_high_premium" in valuation_result.tags
    assert "convertible_premium_expansion" in valuation_result.tags
    assert "convertible_low_ytm" in valuation_result.tags
    assert "low_pe" not in valuation_result.tags
    assert "low_pb" not in valuation_result.tags
