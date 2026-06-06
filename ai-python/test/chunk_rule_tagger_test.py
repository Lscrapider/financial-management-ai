import importlib.util
import json
from pathlib import Path
import sys
import types


def load_tagger_class():
    project_root = Path(__file__).resolve().parents[2]
    service_root = project_root / "ai-python/app/ocr/services"
    for module_name in ("app", "app.ocr", "app.ocr.services"):
        sys.modules[module_name] = types.ModuleType(module_name)
    load_module("app.ocr.services.chunk_tag_schema", service_root / "chunk_tag_schema.py")
    module = load_module("chunk_rule_tagger", service_root / "chunk_rule_tagger.py")
    return module.ChunkRuleTagger


def load_module(module_name, module_path):
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def build_reviewed_json(texts):
    return {
        "taskNo": "ocr-rule-test",
        "content": {
            "paragraphs": [
                {
                    "paragraphNo": index + 1,
                    "sourcePages": [1],
                    "text": text,
                }
                for index, text in enumerate(texts)
            ]
        },
    }


def main():
    tagger = load_tagger_class()()
    reviewed_json = build_reviewed_json(
        [
            "上涨后的回调属于趋势回踩，只要回踩不破，原趋势没有明显破坏。",
            "下跌后企稳回升，跌幅收敛，属于弱势修复。",
            "跌破平台并破位下行，关键支撑失守。",
            "上涨延续，趋势继续上行。",
            "上涨后动能衰减，上攻乏力，趋势开始走弱。",
            "长期横盘后重心抬升，走势转强。",
            "箱体突破后又站不上去，属于假突破。",
            "这只可转债转股溢价率较高，接近强赎，剩余规模过小，正股联动明显。",
            "这只 ETF 存在场内溢价，跟踪误差扩大，基金份额增长但也要关注基金流动性风险。",
            "可转债的正股 PE 较低，但这段重点是转债高溢价和强赎风险。",
        ]
    )
    result = tagger.tag(reviewed_json)
    trend_tags = {
        tag
        for chunk in result["chunks"]
        for tag in chunk["ruleScenes"]["trend"]
    }
    expected = {
        "pullback",
        "repair",
        "breakdown_from_range",
        "continuation",
        "turn_weak",
        "turn_strong",
        "failed_breakout",
    }
    missing = expected - trend_tags
    assert not missing, json.dumps(
        {"missing": sorted(missing), "trendTags": sorted(trend_tags)},
        ensure_ascii=False,
    )
    all_scenes = {
        category: {
            tag
            for chunk in result["chunks"]
            for tag in chunk["ruleScenes"][category]
        }
        for category in ("asset", "valuation", "sentiment", "risk_strategy")
    }
    expected_by_category = {
        "asset": {"convertible_bond", "etf", "fund"},
        "valuation": {"convertible_high_premium", "fund_premium", "fund_tracking_error"},
        "sentiment": {"convertible_stock_linkage"},
        "risk_strategy": {
            "convertible_forced_redeem_risk",
            "convertible_small_balance_risk",
            "fund_liquidity_risk",
        },
    }
    for category, expected_tags in expected_by_category.items():
        missing_tags = expected_tags - all_scenes[category]
        assert not missing_tags, json.dumps(
            {
                "category": category,
                "missing": sorted(missing_tags),
                "tags": sorted(all_scenes[category]),
            },
            ensure_ascii=False,
        )
    convertible_chunk = result["chunks"][-1]["ruleScenes"]
    assert "stock" not in convertible_chunk["asset"], json.dumps(convertible_chunk, ensure_ascii=False)
    assert "low_pe" not in convertible_chunk["valuation"], json.dumps(convertible_chunk, ensure_ascii=False)
    assert "convertible_high_premium" in convertible_chunk["valuation"], json.dumps(
        convertible_chunk,
        ensure_ascii=False,
    )
    assert "convertible_forced_redeem_risk" in convertible_chunk["risk_strategy"], json.dumps(
        convertible_chunk,
        ensure_ascii=False,
    )
    print("chunk rule tagger test passed")


if __name__ == "__main__":
    main()
