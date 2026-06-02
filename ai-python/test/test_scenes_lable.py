import json
from pathlib import Path
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
PROJECT_DIR = ROOT_DIR.parent
sys.path.insert(0, str(ROOT_DIR))

from app.scene_analysis.services.asset_processor import AssetProcessor
from app.scene_analysis.services.base_metrics import BaseMetricsCalculator
from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.services.price_processor import PriceProcessor
from app.scene_analysis.services.risk_strategy_processor import RiskStrategyProcessor
from app.scene_analysis.services.sentiment_processor import SentimentProcessor
from app.scene_analysis.services.trend_processor import TrendProcessor
from app.scene_analysis.services.valuation_processor import ValuationProcessor
from app.scene_analysis.services.volume_processor import VolumeProcessor


DATA_PATH = Path(__file__).with_name("data3.json")
KEY_METRICS = [
    "change_pct",
    "price_rise",
    "price_drop",
    "price_move",
    "volume_ratio_5d",
    "volume_ratio_20d",
    "volume_expand",
    "volume_shrink",
    "volume_spike",
    "high_turnover",
    "low_turnover",
    "position_20d",
    "near_recent_high",
    "near_recent_low",
    "breakout",
    "break_recent_low",
    "close_weak",
    "upper_shadow",
    "large_intraday_move",
    "volatility",
    "support_distance",
    "trading_attention",
    "trading_attention_rise",
    "market_attention_rise",
    "low_attention",
]


def load_message() -> dict:
    payload = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    if isinstance(payload, str):
        payload = json.loads(payload)
    if not isinstance(payload, dict):
        raise ValueError("data.json must contain a JSON object")
    return payload


def main() -> None:
    message = load_message()
    target = message.get("target") or {}

    base_metrics = BaseMetricsCalculator().calculate(message)
    context = SceneAnalysisContext.from_message(message, base_metrics)
    asset_result = AssetProcessor().process(context)
    price_result = PriceProcessor().process(context)
    volume_result = VolumeProcessor().process(context)
    trend_result = TrendProcessor().process(context)
    valuation_result = ValuationProcessor().process(context, trend_result.tags)
    sentiment_result = SentimentProcessor().process(context)
    risk_strategy_result = RiskStrategyProcessor().process(
        context,
        trend_result.tags,
        valuation_result.tags,
        sentiment_result.tags,
    )

    print("target:", target)
    print("metric_count:", len(base_metrics.values))
    print("missing:", base_metrics.missing)
    print("asset:", json.dumps(asset_result.to_dict(), ensure_ascii=False, indent=2))
    print("price:", json.dumps(price_result.to_dict(), ensure_ascii=False, indent=2))
    print("volume:", json.dumps(volume_result.to_dict(), ensure_ascii=False, indent=2))
    print("trend:", json.dumps(trend_result.to_dict(), ensure_ascii=False, indent=2))
    print("valuation:", json.dumps(valuation_result.to_dict(), ensure_ascii=False, indent=2))
    print("sentiment:", json.dumps(sentiment_result.to_dict(), ensure_ascii=False, indent=2))
    print("risk_strategy:", json.dumps(risk_strategy_result.to_dict(), ensure_ascii=False, indent=2))
    print("key_metrics:")
    for key in KEY_METRICS:
        print(f"  {key}: {base_metrics.values.get(key)}")


if __name__ == "__main__":
    main()
