from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.graph.profile_context import messages_with_memory_context, messages_with_psych_profile
from app.agent.graph.state import AgentGraphState, merge_state, message_with_content
from app.core.config import settings

logger = logging.getLogger(__name__)


def planner_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    messages = _planning_messages(state)
    logger.info(
        "agent graph planning start session_id=%s step=%s model=%s base_url=%s thinking_type=%s",
        state["agent_session_id"],
        state.get("step_index", 0),
        settings.deepseek.model,
        settings.deepseek.base_url,
        settings.deepseek.thinking_type,
    )
    planning_message = deps.model.bind_tools(list(deps.tools_by_name.values())).invoke(messages)
    standard_tool_calls = getattr(planning_message, "tool_calls", []) or []
    content = str(getattr(planning_message, "content", "") or "").strip()
    logger.info(
        "agent graph planning done session_id=%s step=%s standard_tool_calls=%s content_preview=%s tool_calls_preview=%s",
        state["agent_session_id"],
        state.get("step_index", 0),
        len(standard_tool_calls),
        _preview(content),
        _tool_calls_preview(standard_tool_calls),
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


def _preview(value: Any, limit: int = 200) -> str:
    return str(value or "").replace("\n", "\\n")[:limit]


def _tool_calls_preview(tool_calls: list[dict[str, Any]], limit: int = 500) -> str:
    preview = []
    for tool_call in tool_calls:
        preview.append({
            "name": tool_call.get("name"),
            "args": tool_call.get("args") or {},
        })
    return _preview(json.dumps(preview, ensure_ascii=False, default=str), limit)
