from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.graph.graph_runner import AgentGraphRunner
from app.agent.runtime.agent_execution_budget import AgentExecutionBudget
from app.agent.runtime.token_usage import AgentTokenUsageCollector


@dataclass
class FakeMessage:
    content: str = ""
    tool_calls: list[dict[str, Any]] | None = None
    usage_metadata: dict[str, Any] | None = None
    response_metadata: dict[str, Any] | None = None

    def __add__(self, other: "FakeMessage") -> "FakeMessage":
        return FakeMessage(
            content=f"{self.content}{other.content}",
            tool_calls=[*(self.tool_calls or []), *(other.tool_calls or [])],
            usage_metadata=other.usage_metadata or self.usage_metadata,
            response_metadata=other.response_metadata or self.response_metadata,
        )


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


class FakeStreamingModel:
    def __init__(
        self,
        responses: list[FakeMessage] | None = None,
        stream_responses: list[FakeMessage] | None = None,
    ) -> None:
        self._responses = responses or []
        self._stream_responses = stream_responses
        self.invoke_messages: list[list[Any]] = []
        self.stream_messages: list[list[Any]] = []

    def bind_tools(self, tools: list[Any]) -> "FakeStreamingModel":
        return self

    def invoke(self, messages: list[Any]) -> FakeMessage:
        self.invoke_messages.append(messages)
        return self._responses.pop(0)

    def stream(self, messages: list[Any]) -> list[FakeMessage]:
        self.stream_messages.append(messages)
        if self._stream_responses is not None:
            return self._stream_responses
        return [
            message("基于工具"),
            message("生成回答。"),
        ]


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
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    profile_calls = 0

    def profile_provider() -> dict[str, Any]:
        nonlocal profile_calls
        profile_calls += 1
        return {"adviceStyle": "explicit_trade_light_position"}

    result = runner.run(
        model=FakeStreamingModel([message("直接回答")]),
        messages=[],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        psych_profile_provider=profile_provider,
    )

    assert result.answer == "直接回答"
    assert profile_calls == 0
    assert [event["phase"] for event in collector.events()] == ["initial_planning"]


def test_context_gate_loads_memory_before_planner_when_needed() -> None:
    collector = AgentTokenUsageCollector()
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    memory_modes: list[str] = []
    model = FakeStreamingModel([
        message('{"profileRequired":false,"memoryRequired":false,"memoryMode":null,"reason":"只需记忆"}'),
        message("继续分析回答"),
    ])

    result = runner.run(
        model=model,
        messages=[FakeMessage(content="这个股票具体分析下")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        memory_provider=lambda mode: memory_modes.append(mode) or "短期记忆内容",
    )

    assert result.answer == "继续分析回答"
    assert memory_modes == ["continue_task"]
    assert any(
        "当前会话短期记忆" in str(getattr(item, "content", ""))
        for item in model.invoke_messages[1]
    )
    assert [event["phase"] for event in collector.events()] == ["context_gate", "initial_planning"]


def test_context_gate_llm_fallback_loads_memory_for_short_followup() -> None:
    collector = AgentTokenUsageCollector()
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    memory_modes: list[str] = []
    model = FakeStreamingModel([
        message('{"profileRequired":false,"memoryRequired":true,"memoryMode":"continue_task","reason":"短追问"}'),
        message("继续分析回答"),
    ])

    result = runner.run(
        model=model,
        messages=[FakeMessage(content="然后呢")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        memory_provider=lambda mode: memory_modes.append(mode) or "短期记忆内容",
    )

    assert result.answer == "继续分析回答"
    assert memory_modes == ["continue_task"]
    assert any(
        "当前会话短期记忆" in str(getattr(item, "content", ""))
        for item in model.invoke_messages[1]
    )
    assert [event["phase"] for event in collector.events()] == ["context_gate", "initial_planning"]


def test_context_gate_still_checks_memory_when_profile_rule_matches() -> None:
    collector = AgentTokenUsageCollector()
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    profile_calls = 0
    memory_modes: list[str] = []
    model = FakeStreamingModel([
        message('{"profileRequired":false,"memoryRequired":true,"memoryMode":"continue_task","reason":"买卖短追问"}'),
        message("继续分析回答"),
    ])

    def profile_provider() -> dict[str, Any]:
        nonlocal profile_calls
        profile_calls += 1
        return {"adviceStyle": "explicit_trade_light_position"}

    result = runner.run(
        model=model,
        messages=[FakeMessage(content="那我能不能买呢")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        psych_profile_provider=profile_provider,
        memory_provider=lambda mode: memory_modes.append(mode) or "短期记忆内容",
    )

    assert result.answer == "继续分析回答"
    assert profile_calls == 1
    assert memory_modes == ["continue_task"]
    assert any(
        "当前会话短期记忆" in str(getattr(item, "content", ""))
        for item in model.invoke_messages[1]
    )
    assert [event["phase"] for event in collector.events()] == ["context_gate", "initial_planning"]


def test_tool_result_answer_streams_final_answer_after_followup_planning() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    deltas: list[str] = []
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    model = FakeStreamingModel([
        message('{"profileRequired":false,"memoryRequired":false,"memoryMode":null,"reason":"明确标的"}'),
        message("", [tool_call]),
        message("基于工具回答"),
        message('{"status":"ready","reason":"已有工具结果"}'),
    ])

    result = runner.run(
        model=model,
        messages=[FakeMessage(content="看看海康威视，然后给我买卖建议")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        answer_delta_callback=deltas.append,
    )

    assert result.answer == "基于工具生成回答。"
    assert deltas == ["基于工具生成回答。"]
    assert any(
        "仅用于工具规划" in str(getattr(item, "content", ""))
        for item in model.invoke_messages[1]
    )
    assert any(
        "不能给出明确买入" in str(getattr(item, "content", ""))
        for item in model.stream_messages[0]
    )
    assert [event["phase"] for event in collector.events()] == [
        "context_gate",
        "initial_planning",
        "tool_followup_planning",
        "answer_readiness_check",
        "final_answer",
    ]


def test_final_stream_clears_planner_message_content_but_keeps_tool_calls() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    model = FakeStreamingModel([
        message("场景信号齐全，现在用知识库 RAG 召回。", [tool_call]),
        message("基于工具回答"),
        message('{"status":"ready","reason":"已有工具结果"}'),
    ])

    result = runner.run(
        model=model,
        messages=[],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        token_usage_collector=collector,
        answer_delta_callback=lambda _delta: None,
    )

    assert result.answer == "基于工具生成回答。"
    final_messages = model.stream_messages[0]
    assert all(
        "RAG 召回" not in str(getattr(item, "content", ""))
        for item in final_messages
    )
    assert any(
        getattr(item, "content", None) == ""
        and getattr(item, "tool_calls", None) == [tool_call]
        for item in final_messages
    )


def test_final_decision_can_backtrack_to_planner() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    followup_call = {"id": "call-2", "name": "knowledge_search", "args": {"query_text": "风险"}}
    deltas: list[str] = []
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())

    result = runner.run(
        model=FakeStreamingModel([
            message("", [tool_call]),
            message("基于工具回答"),
            message('{"status":"need_tool","reason":"缺知识库","planningNudge":"请补 knowledge_search"}'),
            message("", [followup_call]),
            message("补充工具回答"),
            message('{"status":"ready","reason":"证据足够"}'),
        ]),
        messages=[],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        budget=AgentExecutionBudget(max_steps=3),
        token_usage_collector=collector,
        answer_delta_callback=deltas.append,
    )

    assert result.answer == "基于工具生成回答。"
    assert deltas == ["基于工具生成回答。"]
    assert [event["phase"] for event in collector.events()] == [
        "initial_planning",
        "tool_followup_planning",
        "answer_readiness_check",
        "tool_followup_planning",
        "tool_followup_planning",
        "answer_readiness_check",
        "final_answer",
    ]


def test_final_decision_tool_intent_text_backtracks_to_planner() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    followup_call = {"id": "call-2", "name": "knowledge_search", "args": {"query_text": "风险"}}
    deltas: list[str] = []
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())

    result = runner.run(
        model=FakeStreamingModel([
            message('{"profileRequired":false,"memoryRequired":false,"memoryMode":null,"reason":"明确标的"}'),
            message("", [tool_call]),
            message("基于工具回答"),
            message("<DSML><invoke name=\"knowledge_search\">风险策略</invoke></DSML>"),
            message("", [followup_call]),
            message("补充工具回答"),
            message('{"status":"ready","reason":"证据足够"}'),
        ]),
        messages=[FakeMessage(content="看看海康威视，然后给我买卖建议")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        budget=AgentExecutionBudget(max_steps=3),
        token_usage_collector=collector,
        answer_delta_callback=deltas.append,
    )

    assert result.answer == "基于工具生成回答。"
    assert deltas == ["基于工具生成回答。"]
    assert [event["phase"] for event in collector.events()] == [
        "context_gate",
        "initial_planning",
        "tool_followup_planning",
        "answer_readiness_check",
        "tool_followup_planning",
        "tool_followup_planning",
        "answer_readiness_check",
        "final_answer",
    ]


def test_final_stream_gets_guard_when_decision_needs_tool_but_budget_stops() -> None:
    collector = AgentTokenUsageCollector()
    tool_call = {"id": "call-1", "name": "market_quote", "args": {}}
    deltas: list[str] = []
    runner = AgentGraphRunner(tool_call_runner=FakeToolRunner())
    model = FakeStreamingModel(
        [
            message('{"profileRequired":false,"memoryRequired":false,"memoryMode":null,"reason":"明确标的"}'),
            message("", [tool_call]),
            message('{"status":"need_tool","reason":"缺知识库","planningNudge":"请补 knowledge_search"}'),
            message("证据不足，不能继续调用工具，只能基于已有数据给出保守结论。"),
        ],
        stream_responses=[
            message("需要补查知识库。\n< | | DSML | | tool_calls>\n"),
            message("< | | DSML | | invoke name=\"knowledge_search\">风险</invoke>"),
        ],
    )

    result = runner.run(
        model=model,
        messages=[FakeMessage(content="看看海康威视，然后给我买卖建议")],
        tools_by_name={},
        tool_message_type=FakeToolMessage,
        quote_result_provider=lambda: {},
        agent_session_id="session-1",
        budget=AgentExecutionBudget(max_steps=1),
        token_usage_collector=collector,
        answer_delta_callback=deltas.append,
    )

    assert result.answer == "证据不足，不能继续调用工具，只能基于已有数据给出保守结论。"
    assert deltas == []
    assert any(
        "不能再调用任何工具" in str(getattr(item, "content", ""))
        for item in model.stream_messages[0]
    )
    assert any(
        "不得输出 DSML" in str(getattr(item, "content", ""))
        for item in model.stream_messages[0]
    )
    assert [event["phase"] for event in collector.events()] == [
        "context_gate",
        "initial_planning",
        "answer_readiness_check",
        "final_answer",
        "final_answer",
    ]


def test_tool_signature_normalizes_unordered_scope_args() -> None:
    budget = AgentExecutionBudget()

    first = budget.signature({
        "name": "scene_signal_context",
        "args": {
            "target_codes": ["123456", "321000"],
            "scenes": ["trend", "valuation"],
        },
    })
    second = budget.signature({
        "name": "scene_signal_context",
        "args": {
            "scenes": ["valuation", "trend"],
            "target_codes": ["321000", "123456"],
        },
    })

    assert first == second


def test_default_agent_execution_budget_limits_are_doubled() -> None:
    budget = AgentExecutionBudget()

    assert budget.max_steps == 6
    assert budget.max_tool_calls_total == 10
    assert budget.max_tool_calls_per_step == 4
    assert budget.timeout_seconds == 50.0
    assert budget.max_final_backtracks == 2
