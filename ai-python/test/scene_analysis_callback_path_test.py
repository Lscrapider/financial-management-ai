from app.messaging.models import IncomingMessage
from app.scene_analysis.handlers.retrieval_embedding_handler import RetrievalEmbeddingHandler


def test_retrieval_embedding_handler_uses_callback_path_from_message():
    callback_client = FakeCallbackClient()
    handler = RetrievalEmbeddingHandler(FakeEmbeddingEngine(), callback_client)
    message = IncomingMessage(
        body={
            "taskNo": "material-query",
            "callbackToken": "callback-token",
            "callbackPath": "/api/ai/knowledge-material/tasks/{taskNo}/callback",
            "retrievalTasks": [
                {
                    "scene": "knowledge",
                    "chunkCount": 2,
                    "currentTags": {},
                    "queryText": "低估值银行股的风险控制",
                }
            ],
        },
        routing_key="scene.analysis.retrieval.embedding",
        delivery_tag=1,
        attempt=1,
    )

    handler.handle(message)

    assert callback_client.callback_path == "/api/ai/knowledge-material/tasks/{taskNo}/callback"
    assert callback_client.task_no == "material-query"
    assert callback_client.callback_token == "callback-token"
    assert callback_client.retrieval_embeddings[0]["queryEmbedding"] == [0.1, 0.2, 0.3]


class FakeEmbeddingEngine:
    def embed(self, texts: list[str]) -> list[list[float]]:
        assert texts == ["低估值银行股的风险控制"]
        return [[0.1, 0.2, 0.3]]


class FakeCallbackClient:
    def __init__(self) -> None:
        self.task_no = ""
        self.callback_token = ""
        self.callback_path = None
        self.retrieval_embeddings = []

    def submit_retrieval_embeddings(
        self,
        task_no: str,
        callback_token: str,
        retrieval_embeddings: list[dict],
        callback_path: str | None = None,
    ) -> None:
        self.task_no = task_no
        self.callback_token = callback_token
        self.retrieval_embeddings = retrieval_embeddings
        self.callback_path = callback_path
