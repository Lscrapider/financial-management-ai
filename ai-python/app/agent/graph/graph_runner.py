from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from app.agent.graph.nodes import (
    context_gate_node,
    final_decision_node,
    final_stream_node,
    load_memory_node,
    load_profile_node,
    planner_node,
    tool_node,
)
from app.agent.graph.routing import (
    route_after_final_decision,
    route_after_context_gate,
    route_after_load_profile,
    route_after_planner,
    route_after_tools,
)
from app.agent.graph.state import AgentGraphDeps, AgentGraphRunResult, AgentGraphState, initial_state
from app.agent.runtime.answer_generator import AgentAnswerGenerator
from app.agent.runtime.tool_call_budget import ToolCallBudget
from app.agent.runtime.tool_call_runner import ToolCallRunner
from app.agent.runtime.token_usage import AgentTokenUsageCollector

logger = logging.getLogger(__name__)


class AgentGraphRunner:
    def __init__(
        self,
        tool_call_runner: ToolCallRunner | None = None,
        answer_generator: AgentAnswerGenerator | None = None,
    ) -> None:
        self._tool_call_runner = tool_call_runner or ToolCallRunner()
        self._answer_generator = answer_generator or AgentAnswerGenerator()

    def run(
        self,
        model: Any,
        messages: list[Any],
        tools_by_name: dict[str, Any],
        tool_message_type: Any,
        quote_result_provider: Callable[[], dict[str, Any]],
        agent_session_id: str,
        budget: ToolCallBudget | None = None,
        token_usage_collector: AgentTokenUsageCollector | None = None,
        answer_delta_callback: Callable[[str], None] | None = None,
        psych_profile_provider: Callable[[], dict[str, Any] | None] | None = None,
        memory_provider: Callable[[str], str | None] | None = None,
    ) -> AgentGraphRunResult:
        graph = self._build_graph()
        deps = AgentGraphDeps(
            model=model,
            tools_by_name=tools_by_name,
            tool_message_type=tool_message_type,
            answer_generator=self._answer_generator,
            tool_call_runner=self._tool_call_runner,
            quote_result_provider=quote_result_provider,
            psych_profile_provider=psych_profile_provider,
            memory_provider=memory_provider,
            token_usage_collector=token_usage_collector,
            answer_delta_callback=answer_delta_callback,
        )
        logger.info("agent graph run start session_id=%s", agent_session_id)
        final_state = graph.invoke(initial_state(
            messages=messages,
            agent_session_id=agent_session_id,
            deps=deps,
            budget=budget or ToolCallBudget(),
        ))
        answer = final_state.get("answer")
        logger.info("agent graph run done session_id=%s answer_len=%s", agent_session_id, len(answer or ""))
        return AgentGraphRunResult(answer=answer)

    def _build_graph(self) -> Any:
        try:
            from langgraph.graph import END, StateGraph
        except ImportError as exc:
            raise RuntimeError("langgraph is not installed") from exc

        graph = StateGraph(AgentGraphState)
        graph.add_node("context_gate", context_gate_node)
        graph.add_node("load_profile", load_profile_node)
        graph.add_node("load_memory", load_memory_node)
        graph.add_node("planner", planner_node)
        graph.add_node("tools", tool_node)
        graph.add_node("final_decision", final_decision_node)
        graph.add_node("final_stream", final_stream_node)
        graph.set_entry_point("context_gate")
        graph.add_conditional_edges("context_gate", route_after_context_gate, {
            "load_profile": "load_profile",
            "load_memory": "load_memory",
            "planner": "planner",
        })
        graph.add_conditional_edges("load_profile", route_after_load_profile, {
            "load_memory": "load_memory",
            "planner": "planner",
        })
        graph.add_edge("load_memory", "planner")
        graph.add_conditional_edges("planner", route_after_planner, {
            "tools": "tools",
            "final_decision": "final_decision",
        })
        graph.add_conditional_edges("tools", route_after_tools, {
            "planner": "planner",
            "final_decision": "final_decision",
        })
        graph.add_conditional_edges("final_decision", route_after_final_decision, {
            "planner": "planner",
            "final_stream": "final_stream",
        })
        graph.add_edge("final_stream", END)
        return graph.compile()
