import importlib.util
from pathlib import Path


def load_aggregator_class():
    project_root = Path(__file__).resolve().parents[2]
    module_path = project_root / "ai-python/app/ocr/services/chunk_tag_aggregation.py"
    spec = importlib.util.spec_from_file_location("chunk_tag_aggregation", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load module: {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.InMemoryChunkTagAggregator


def main():
    aggregator = load_aggregator_class()()
    first = {"chunkId": "t1:chunk:0001", "chunkIndex": 1}
    second = {"chunkId": "t1:chunk:0002", "chunkIndex": 2}

    assert not aggregator.add("t1", 2, first).ready
    ready = aggregator.add("t1", 2, second)
    assert ready.ready
    assert [chunk["chunkId"] for chunk in ready.chunks] == ["t1:chunk:0001", "t1:chunk:0002"]

    retry_ready = aggregator.add("t1", 2, second)
    assert retry_ready.ready
    assert [chunk["chunkId"] for chunk in retry_ready.chunks] == ["t1:chunk:0001", "t1:chunk:0002"]

    aggregator.complete("t1")
    assert not aggregator.add("t1", 2, second).ready
    print("chunk tag aggregation test passed")


if __name__ == "__main__":
    main()
