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
            "不论国内还是国外, 研究机构投资者的羊群效应的相关实证文献都将机构投资者分为净买基金和净卖基金。其中, 对于某只股票来说, 如果某只基金在某时期内买入了该股票, 而没有卖出过该股票, 那么该基金相对于该股票来说是净买基金, 同样方式可以定义净卖基金。而实际上, 机构投资者在某个时期内的交易行为应该分为: 只买不卖、只卖不买、既买又卖以及不交易。由于不交易并没有提供任何有关行为主体的信息, 故不加以研究。现有文献中的净买或者净卖机构可能在某个时期内进行了多种交易, 也可能只进行了一次或多次买或卖的交易行为, 其综合结果表现为净买或净卖, 因此对其投资过程中的交易行为进行研究, 能够揭示出更多有关机构投资者的信息, 而现有文献仅仅研究了综合结果。此外, 文献[1] 分别用 BHM 和 SHM 作为衡量机构投资者买和卖的羊群效应指标。这种方法的不足是它没有考虑到所有的样本股票而只是选取了其中满足一定条件的样本股票。因此不能全面的反映机构投资者的买和卖的交易情况。本文在文献[10] 研究的基础上, 对假设做了更加切合实际的扩展, 将中国证券投资基金的投资行为分为三种: 只买不卖、只卖不买和既买又卖, 使用文献[1] 的检验方法, 研究了中国投资基金的羊群效应, 发现中国投资基金的交易行为主要集中在既买又卖的行为上, 约占 50%。中国证券投资基金在只买不卖方面的羊群效应高于美国互助基金相应的羊群效应, 在既买又卖和只卖不买方面并不高于美国互助基金的买的羊群效应。而文献[10] 则认为中国证券投资基金的羊群效应高于美国互助基金的羊群效应。中国投资基金对各种股本组合、信息技术股票和新股的只买不卖交易中"
        ]
    )
    result = tagger.tag(reviewed_json)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
