from pathlib import Path
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from app.scene_analysis.services.market_context import MarketContextBuilder


def test_market_context_includes_valuation_facts() -> None:
    message = {
        "target": {
            "type": "STOCK",
            "code": "000001",
            "name": "平安银行",
        },
        "marketData": {
            "latestPrice": 10,
            "peTtm": 6.5,
            "peDynamic": 6.1,
            "peStatic": 7.0,
            "pbRatio": 0.8,
            "totalMarketValue": 1000,
            "floatMarketValue": 800,
        },
        "valuationData": {
            "peTtm": 6.4,
            "pbRatio": 0.79,
        },
        "valuationHistory": [
            {"tradeDate": "2026-06-05", "peTtm": 6.4, "pbMrq": 0.79},
            {"tradeDate": "2026-06-04", "peTtm": 8.0, "pbMrq": 0.9},
            {"tradeDate": "2026-06-03", "peTtm": 10.0, "pbMrq": 1.1},
        ],
        "dividendHistory": [
            {
                "reportDate": "2025-12-31",
                "pretaxBonusRmb": 2.0,
                "dividendRatio": 0.02,
                "implPlanProfile": "10派2.00元(含税)",
                "assignProgress": "实施",
            }
        ],
    }

    market_context = MarketContextBuilder().build(message)
    valuation = market_context["valuation"]["data"]

    assert market_context["snapshot"]["meta"]["数据范围"] == "实时行情快照"
    assert market_context["snapshot"]["data"]["latestPrice"] == 10
    assert valuation["current"]["peTtm"] == 6.4
    assert valuation["current"]["peDynamic"] == 6.1
    assert valuation["current"]["pbRatio"] == 0.79
    assert valuation["historySummary"]["peTtm"]["count"] == 3
    assert valuation["historySummary"]["pbMrq"]["percentileRank"] == 1 / 3
    assert valuation["dividend"]["estimatedDividendYieldPct"] == 2.0
