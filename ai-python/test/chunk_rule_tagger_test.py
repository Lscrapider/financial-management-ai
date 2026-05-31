import importlib.util
import json
from pathlib import Path


def load_tagger_class():
    project_root = Path(__file__).resolve().parents[2]
    module_path = project_root / "ai-python/app/ocr/services/chunk_rule_tagger.py"
    spec = importlib.util.spec_from_file_location("chunk_rule_tagger", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.ChunkRuleTagger


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
            "放量上涨看似强势，但追高风险很大"
        ]
    )
    result = tagger.tag(reviewed_json)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
