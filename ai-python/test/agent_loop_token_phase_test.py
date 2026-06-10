from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.planning.agent_planner import AgentPlan
from app.agent.runtime.agent_loop_runner import AgentLoopRunner
from app.agent.runtime.token_usage import AgentTokenUsageCollector


@dataclass
class FakeMessage:
    content: str = ""
    tool_calls: list[dict[str, Any]] | None = None
    usage_metadata: dict[str, Any] | None = None
    response_metadata: dict[str, Any] | None = None


class FakePlanner:
    def __init__(self, plans: list[AgentPlan]) -> None:
        self._plans = plans
        self.messages_seen: list[list[Any]] = []

    def plan(self, model: Any, messages: list[Any], tools: list[Any], agent_session_id: str) -> AgentPlan:
        self.messages_seen.append(messages)
        return self._plans.pop(0)


class FakeToolRunner:
    def run_standard_tools(
        self,
        tool_calls: list[dict[str, Any]],
        tools_by_name: dict[str, Any],
        agent_session_id: str,
        tool_message_type: Any,
        step_index: int = 0,
    ) -> Any:
        return type(
            "RunResult",
            (),
            {
                "trace_entries": [],
                "tool_messages": [tool_message_type(content="工具结果", tool_call_id="call-1")],
                "all_failed": False,
            },
        )()


@dataclass
class FakeToolMessage:
    content: str
    tool_call_id: str


def message(content: str = "", tool_calls: list[dict[str, Any]] | None = None) -> FakeMessage:
    return FakeMessage(
        content=content,
        tool_calls=tool_calls or [],
        usage_metadata={"input_tokens": 10, "output_tokens": 2, "total_tokens": 12},
        response_metadata={"model_name": "fake-model"},
    )


def test_direct_answer_records_direct_answer_phase() -> None:
    collector = AgentTokenUsageCollector()
    runner = AgentLoopRunner(
        planner=FakePlanner([AgentPlan(message("直接回答"), [], "直接回答")]),
        tool_call_runner=FakeToolRunner(),
    )

    answer = runner.run(
        model=object(),
        messages=[],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
    )

    assert answer == "直接回答"
    assert [event["phase"] for event in collector.events()] == ["direct_answer"]


def test_tool_result_answer_records_tool_result_answer_phase() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    runner = AgentLoopRunner(
        planner=FakePlanner([
            AgentPlan(message("", [tool_call]), [tool_call], ""),
            AgentPlan(message("基于工具回答"), [], "基于工具回答"),
        ]),
        tool_call_runner=FakeToolRunner(),
    )

    answer = runner.run(
        model=object(),
        messages=[],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
    )

    assert answer == "基于工具回答"
    assert [event["phase"] for event in collector.events()] == [
        "initial_planning",
        "tool_result_answer",
    ]
