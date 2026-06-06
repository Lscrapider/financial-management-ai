import importlib.util
from pathlib import Path
import sys
import types


def load_corrector_class():
    project_root = Path(__file__).resolve().parents[2]
    service_root = project_root / "ai-python/app/ocr/services"
    for module_name in ("app", "app.ocr", "app.ocr.services"):
        sys.modules[module_name] = types.ModuleType(module_name)
    load_module("app.ocr.services.chunk_tag_schema", service_root / "chunk_tag_schema.py")
    module = load_module("chunk_tag_corrector", service_root / "chunk_tag_corrector.py")
    return module.ChunkTagCorrector


def load_module(module_name, module_path):
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def main():
    corrector = load_corrector_class()()
    result = corrector.correct(
        {
            "taskNo": "ocr-corrector-test",
            "chunk": {
                "chunkId": "ocr-corrector-test:chunk:0001",
                "chunkIndex": 1,
                "text": "没有标签的段落",
                "pageNos": [1],
                "paragraphNos": [1],
            },
            "ruleTagging": {
                "ruleScenesWithConfidence": {},
                "qualityGate": {
                    "confidenceThreshold": 0.75,
                },
            },
        }
    )
    assert result["metadata"]["deleted"] is True
    assert all(not tags for tags in result["metadata"]["scenes"].values())
    tagged = corrector.correct(
        {
            "taskNo": "ocr-corrector-test",
            "chunk": {
                "chunkId": "ocr-corrector-test:chunk:0002",
                "chunkIndex": 2,
                "text": "可转债强赎风险和 ETF 跟踪误差",
                "pageNos": [1],
                "paragraphNos": [2],
            },
            "ruleTagging": {
                "ruleScenesWithConfidence": {},
                "qualityGate": {
                    "confidenceThreshold": 0.75,
                },
            },
            "llmTagging": {
                "llmScenes": {
                    "asset": ["convertible_bond"],
                    "valuation": ["convertible_high_premium", "fund_tracking_error", "low_pe"],
                    "risk_strategy": ["convertible_forced_redeem_risk", "fund_tracking_deviation_risk"],
                },
            },
        }
    )
    scenes = tagged["metadata"]["scenes"]
    assert scenes["asset"] == ["convertible_bond"]
    assert scenes["valuation"] == ["convertible_high_premium"]
    assert scenes["risk_strategy"] == ["convertible_forced_redeem_risk"]
    assert tagged["metadata"]["deleted"] is False
    print("chunk tag corrector deleted test passed")


if __name__ == "__main__":
    main()
