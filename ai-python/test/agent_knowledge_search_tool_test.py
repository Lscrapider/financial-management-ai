from pathlib import Path
import json
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from langchain_core.tools import tool

from app.agent.tools.knowledge_search_tool import KnowledgeSearchTool
from app.agent.tools.tool_registry import AgentToolContext, AgentToolRegistry


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


def test_knowledge_search_normalizes_boolean_tag_map() -> None:
    gateway = FakeGatewayClient({"success": True, "data": []})
    tool = KnowledgeSearchTool(
        data_gateway_client=gateway,
        embedding_provider=FakeEmbeddingProvider([0.1, 0.2, 0.3]),
    )

    tool.invoke(
        data_gateway_url="http://java/internal/agent/data",
        agent_session_id="agent-session",
        session_secret="secret",
        query_text="下降趋势 止损计划",
        scenes=["trend", "risk_strategy"],
        tags={
            "downtrend": True,
            "breakdown_from_range": True,
            "stop_loss_plan": True,
            "ignored_tag": False,
        },
        limit=4,
    )

    assert gateway.last_params["tags"] == {
        "trend": ["downtrend", "breakdown_from_range", "stop_loss_plan"],
        "risk_strategy": ["downtrend", "breakdown_from_range", "stop_loss_plan"],
    }


def test_knowledge_search_langchain_schema_accepts_boolean_tags() -> None:
    fake_tool = FakeKnowledgeSearchTool()
    registry = AgentToolRegistry(knowledge_search_tool=fake_tool)
    tools = registry.build_langchain_tools(
        AgentToolContext(
            data_gateway_url="http://java/internal/agent/data",
            agent_session_id="agent-session",
            session_secret="secret",
        ),
        tool,
    )

    result = tools["knowledge_search"].invoke({
        "query_text": "下降趋势 止损计划",
        "scenes": ["trend", "risk_strategy"],
        "tags": {"downtrend": True, "stop_loss_plan": True},
        "limit": 4,
    })

    assert result == "ok"
    assert fake_tool.last_tags == {"downtrend": True, "stop_loss_plan": True}


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


class FakeKnowledgeSearchTool:
    def __init__(self) -> None:
        self.last_tags = None

    def invoke(self, **kwargs) -> str:
        self.last_tags = kwargs["tags"]
        return "ok"
