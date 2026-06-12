from __future__ import annotations
import logging
from typing import Any

from app.agent.graph.profile_context import messages_with_memory_context, messages_with_psych_profile
from app.agent.graph.state import AgentGraphState, merge_state, message_with_content
from app.core.config import settings

logger = logging.getLogger(__name__)


def planner_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    messages = _planning_messages(state)
    tools_by_name = _allowed_tools_by_name(state)
    logger.info(
        "agent graph planning start session_id=%s step=%s model=%s thinking_type=%s query_depth=%s tool_count=%s",
        state["agent_session_id"],
        state.get("step_index", 0),
        settings.deepseek.model,
        settings.deepseek.thinking_type,
        state.get("query_depth"),
        len(tools_by_name),
    )
    planning_message = deps.model.bind_tools(list(tools_by_name.values())).invoke(messages)
    standard_tool_calls = _allowed_tool_calls(getattr(planning_message, "tool_calls", []) or [], tools_by_name)
    content = str(getattr(planning_message, "content", "") or "").strip()
    logger.info(
        "agent graph planning done session_id=%s step=%s standard_tool_calls=%s content_len=%s",
        state["agent_session_id"],
        state.get("step_index", 0),
        len(standard_tool_calls),
        len(content),
    )
    _record_planning_usage(state, planning_message)
    return merge_state(
        state,
        planning_message=planning_message,
        pending_tool_calls=standard_tool_calls,
        plan_content=content,
    )


def _planning_messages(state: AgentGraphState) -> list[Any]:
    base_messages = messages_with_psych_profile(
        state["messages"],
        state.get("psych_profile"),
        purpose="planner",
    )
    base_messages = messages_with_memory_context(
        base_messages,
        state.get("memory_context"),
        purpose="planner",
    )
    messages = [
        *base_messages,
        *state.get("scratchpad", []),
    ]
    intent_hint = state.get("planner_intent_hint")
    if intent_hint:
        intent_message = message_with_content(
            state["messages"],
            "本轮工具规划约束：\n"
            f"- 问题深度：{state.get('query_depth') or 'brief'}\n"
            f"- 允许工具：{', '.join(state.get('allowed_tool_names') or [])}\n"
            f"- 约束说明：{intent_hint}",
        )
        if intent_message is not None:
            messages.append(intent_message)
    planning_nudges = state.get("planning_nudges") or []
    if not planning_nudges:
        return messages
    # final_decision 回退时只追加缺失证据提示，画像上下文由前置 load_profile 统一注入。
    nudge_message = message_with_content(
        state["messages"],
        "补充工具规划提示：\n" + "\n".join(f"- {item}" for item in planning_nudges),
    )
    return [*messages, nudge_message] if nudge_message is not None else messages


def _record_planning_usage(state: AgentGraphState, planning_message: Any) -> None:
    collector = state["deps"].token_usage_collector
    if collector is None:
        return
    phase = "tool_followup_planning" if state.get("scratchpad") else "initial_planning"
    collector.add_message(planning_message, phase)


def _allowed_tools_by_name(state: AgentGraphState) -> dict[str, Any]:
    tools_by_name = state["deps"].tools_by_name
    allowed_tool_names = state.get("allowed_tool_names") or list(tools_by_name.keys())
    return {
        tool_name: tools_by_name[tool_name]
        for tool_name in allowed_tool_names
        if tool_name in tools_by_name
    }


def _allowed_tool_calls(tool_calls: list[dict[str, Any]], tools_by_name: dict[str, Any]) -> list[dict[str, Any]]:
    if not tools_by_name:
        return tool_calls
    allowed_names = set(tools_by_name)
    return [
        tool_call
        for tool_call in tool_calls
        if str(tool_call.get("name") or "") in allowed_names
    ]
