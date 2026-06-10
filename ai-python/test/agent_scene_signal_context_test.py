from pathlib import Path
import sys

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from app.scene_analysis.models import BaseMetrics, SceneModuleResult
from app.agent.scene_signal.scene_signal_runner import SceneSignalRunner
from app.agent.tools.scene_signal_context_tool import SceneSignalContextTool


def test_scene_signal_runner_reuses_dependencies_and_returns_requested_payload_only() -> None:
    calls: list[str] = []
    runner = SceneSignalRunner(
        base_metrics_calculator=FakeBaseMetricsCalculator(),
        context_factory=lambda _message, _base_metrics: object(),
        trend_processor=FakeProcessor("trend", calls, {"downtrend": 0.9}),
        valuation_processor=FakeProcessor("valuation", calls, {"low_pb": 0.8}),
        sentiment_processor=FakeProcessor("sentiment", calls, {"weak_sentiment": 0.7}),
        risk_strategy_processor=FakeRiskStrategyProcessor(calls),
    )

    result = runner.run({"target": {"type": "STOCK"}}, ["risk_strategy"])

    assert calls == ["trend", "valuation", "sentiment", "risk_strategy"]
    assert list(result.keys()) == ["riskStrategy"]
    risk_payload = result["riskStrategy"]
    assert risk_payload["score"] == 0.88
    assert risk_payload["level"] == "high"
    assert risk_payload["direction"] == "risk"
    assert risk_payload["tags"] == {"position_control": 0.88}
    assert risk_payload["evidence"] == ["仓位控制触发"]
    assert risk_payload["queryText"] == "风险策略 query"


def test_scene_signal_context_tool_calls_gateway_and_preserves_processor_payload() -> None:
    calls: list[str] = []
    runner = SceneSignalRunner(
        base_metrics_calculator=FakeBaseMetricsCalculator(),
        context_factory=lambda _message, _base_metrics: object(),
        trend_processor=FakeProcessor("trend", calls, {"downtrend": 0.9}),
        valuation_processor=FakeProcessor("valuation", calls, {"low_pb": 0.8}),
        sentiment_processor=FakeProcessor("sentiment", calls, {"weak_sentiment": 0.7}),
        risk_strategy_processor=FakeRiskStrategyProcessor(calls),
    )
    gateway = FakeGatewayClient({
        "success": True,
        "data": [{"target": {"type": "STOCK", "code": "002958"}}],
    })
    tool = SceneSignalContextTool(data_gateway_client=gateway, runner=runner)

    result = tool.query(
        data_gateway_url="http://java/internal/agent/data",
        agent_session_id="agent-session",
        session_secret="secret",
        target_type="stock",
        target_code="002958",
        target_name="青农商行",
        scenes=["risk_strategy"],
        total_chunks=15,
    )

    assert gateway.last_action == "scene.signal_data"
    assert gateway.last_params["totalChunks"] == 15
    assert "dataCompleteness" not in result
    assert result["sceneSignals"]["riskStrategy"]["tags"] == {"position_control": 0.88}
    assert result["sceneSignals"]["riskStrategy"]["queryText"] == "风险策略 query"


class FakeBaseMetricsCalculator:
    def calculate(self, _message: dict) -> BaseMetrics:
        return BaseMetrics()


class FakeProcessor:
    def __init__(self, module: str, calls: list[str], tags: dict[str, float]) -> None:
        self._module = module
        self._calls = calls
        self._tags = tags

    def process(self, *_args) -> SceneModuleResult:
        self._calls.append(self._module)
        return SceneModuleResult(
            module=self._module,
            score=0.5,
            level="medium",
            direction="neutral",
            tags=self._tags,
            evidence=[f"{self._module} evidence"],
            query_text_override=f"{self._module} query",
        )


class FakeRiskStrategyProcessor:
    def __init__(self, calls: list[str]) -> None:
        self._calls = calls

    def process(self, _context, trend_tags, valuation_tags, sentiment_tags) -> SceneModuleResult:
        self._calls.append("risk_strategy")
        assert trend_tags == {"downtrend": 0.9}
        assert valuation_tags == {"low_pb": 0.8}
        assert sentiment_tags == {"weak_sentiment": 0.7}
        return SceneModuleResult(
            module="risk_strategy",
            score=0.88,
            level="high",
            direction="risk",
            tags={"position_control": 0.88},
            evidence=["仓位控制触发"],
            query_text_override="风险策略 query",
        )


class FakeGatewayClient:
    def __init__(self, response: dict) -> None:
        self._response = response
        self.last_action = None
        self.last_params = None

    def query(self, **kwargs) -> dict:
        self.last_action = kwargs["action"]
        self.last_params = kwargs["params"]
        return self._response
