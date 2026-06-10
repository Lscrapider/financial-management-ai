from pathlib import Path
import json
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from app.agent.tools.knowledge_search_tool import KnowledgeSearchTool


def test_knowledge_search_returns_only_filename_and_content_to_llm() -> None:
    gateway = FakeGatewayClient({
        "success": True,
        "data": [
            {
                "filename": "低估值投资方法.pdf",
                "content": "低估值不等于低风险。",
                "chunkId": 123,
                "taskNo": "ocr-test",
                "semanticScore": 0.82,
                "tagMatchScore": 0.5,
                "crossSceneScore": 0.1,
                "matchedTags": ["low_pb"],
            }
        ],
    })
    tool = KnowledgeSearchTool(
        data_gateway_client=gateway,
        embedding_provider=FakeEmbeddingProvider([0.1, 0.2, 0.3]),
    )

    result = tool.invoke(
        data_gateway_url="http://java/internal/agent/data",
        agent_session_id="agent-session",
        session_secret="secret",
        query_text="低PB高股息银行股是否是价值陷阱",
        scenes=["valuation"],
        tags={"valuation": ["low_pb"]},
        limit=5,
    )

    payload = json.loads(result)
    assert payload == {
        "chunks": [
            {
                "filename": "低估值投资方法.pdf",
                "content": "低估值不等于低风险。",
            }
        ]
    }
    assert gateway.last_action == "knowledge.search"
    assert gateway.last_params["queryEmbedding"] == [0.1, 0.2, 0.3]


class FakeEmbeddingProvider:
    def __init__(self, embedding: list[float]) -> None:
        self._embedding = embedding

    def embed_query(self, _text: str) -> list[float]:
        return self._embedding


class FakeGatewayClient:
    def __init__(self, response: dict) -> None:
        self._response = response
        self.last_action = None
        self.last_params = None

    def query(self, **kwargs) -> dict:
        self.last_action = kwargs["action"]
        self.last_params = kwargs["params"]
        return self._response
