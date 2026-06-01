import importlib.util
from pathlib import Path
import sys
import types


def load_embedding_service_class():
    project_root = Path(__file__).resolve().parents[2]
    service_root = project_root / "ai-python/app/ocr/services"
    for module_name in ("app", "app.ocr", "app.ocr.services"):
        sys.modules[module_name] = types.ModuleType(module_name)
    load_module("app.ocr.services.chunk_tag_schema", service_root / "chunk_tag_schema.py")
    module = load_module("embedding_service", service_root / "embedding_service.py")
    return module.EmbeddingService


def load_module(module_name, module_path):
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


class FakeEmbeddingEngine:
    def embed(self, texts):
        return [[1.0, 0.0, 0.0] for _ in texts]


def main():
    service = load_embedding_service_class()(FakeEmbeddingEngine(), "fake-model")
    reviewed_json = {
        "taskNo": "ocr-embedding-test",
        "content": {
            "paragraphs": [
                {
                    "paragraphNo": 1,
                    "sourcePages": [1],
                    "text": "放量上涨后控制仓位",
                    "metadata": {
                        "scenes": {
                            "asset": ["stock"],
                            "price": [],
                            "volume": ["volume_expand"],
                            "trend": [],
                            "valuation": [],
                            "sentiment": [],
                            "risk_strategy": ["position_control"],
                        },
                        "keywords": [],
                        "summary": "",
                        "tagging": {"ruleTagging": {"tagVersion": "rule-v1.1"}},
                    },
                },
                {
                    "paragraphNo": 2,
                    "sourcePages": [2],
                    "text": "没有有效标签的段落",
                    "metadata": {
                        "deleted": True,
                        "scenes": {
                            "asset": [],
                            "price": [],
                            "volume": [],
                            "trend": [],
                            "valuation": [],
                            "sentiment": [],
                            "risk_strategy": [],
                        },
                    },
                },
                {
                    "paragraphNo": 3,
                    "sourcePages": [3],
                    "text": "低估值股票控制风险",
                    "metadata": {
                        "scenes": {
                            "asset": ["stock"],
                            "price": [],
                            "volume": [],
                            "trend": [],
                            "valuation": ["low_pe"],
                            "sentiment": [],
                            "risk_strategy": ["risk_control"],
                        },
                    },
                }
            ]
        },
    }
    chunks = service.embed(reviewed_json)
    assert len(chunks) == 2
    metadata = chunks[0].metadata
    assert chunks[0].chunk_id == "ocr-embedding-test:chunk:0001"
    assert chunks[1].chunk_id == "ocr-embedding-test:chunk:0003"
    assert metadata["scenes"]["volume"] == ["volume_expand"]
    assert metadata["scenes"]["risk_strategy"] == ["position_control"]
    assert metadata["keywords"] == []
    assert metadata["summary"] == ""
    assert metadata["deleted"] is False
    assert metadata["tagging"]["ruleTagging"]["tagVersion"] == "rule-v1.1"
    print("embedding metadata test passed")


if __name__ == "__main__":
    main()
