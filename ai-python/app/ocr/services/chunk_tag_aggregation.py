from dataclasses import dataclass, field
from threading import Lock


@dataclass
class ChunkTagAggregationResult:
    ready: bool
    chunks: list[dict] = field(default_factory=list)


class InMemoryChunkTagAggregator:
    def __init__(self) -> None:
        self._lock = Lock()
        self._states: dict[str, dict] = {}

    def add(self, task_no: str, total_chunk_count: int, chunk_result: dict) -> ChunkTagAggregationResult:
        with self._lock:
            state = self._states.setdefault(
                task_no,
                {
                    "totalChunkCount": total_chunk_count,
                    "chunks": {},
                },
            )
            state["totalChunkCount"] = max(int(state["totalChunkCount"]), int(total_chunk_count))
            state["chunks"][chunk_result["chunkId"]] = chunk_result
            if len(state["chunks"]) < state["totalChunkCount"]:
                return ChunkTagAggregationResult(ready=False)

            chunks = sorted(
                state["chunks"].values(),
                key=lambda item: int(item.get("chunkIndex") or 0),
            )
            return ChunkTagAggregationResult(ready=True, chunks=chunks)

    def complete(self, task_no: str) -> None:
        with self._lock:
            self._states.pop(task_no, None)
