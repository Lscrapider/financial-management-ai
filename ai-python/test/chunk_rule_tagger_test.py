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
            "股权投资的基础条件(第二部分)27. 做股票的正确思想是找到优质的公司，在较低的价位（如前期有大幅的走高，至少日落至少3），在政策和行业方向向好的时机逐步介入。没有好的标的，最好不要做。如果在多方面思考缺陷，搏取运气，那么就会有很多的概率为负的缺陷。28. 简单来说投资就要解决买什么、怎么买及如何卖出的问题。吴什么相对简单，难点是怎么买。如何判断是买入还是卖出，一定要打开期权价格看走势。距离底部价格10倍以上的不介入。如果有长期下跌区的趋势，没有搭建斗牛到一年平台的素质也不要介入，不乘以一时的小利去接飞刀。29. 看好一只股票想要长期介入时，一定要打开期权价格看走势。"
        ]
    )
    result = tagger.tag(reviewed_json)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
