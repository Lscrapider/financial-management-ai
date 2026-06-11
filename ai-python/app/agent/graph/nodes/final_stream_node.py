from __future__ import annotations

import logging

from app.agent.graph.profile_context import messages_with_memory_context
from app.agent.graph.state import AgentGraphState, FinalDecision, merge_state, message_with_content

logger = logging.getLogger(__name__)


def final_stream_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    scratchpad = _scratchpad_with_final_guard(state)
    if scratchpad:
        answer = deps.answer_generator.answer_from_scratchpad(
            model=deps.model,
            messages=messages_with_memory_context(
                state["messages"],
                state.get("memory_context"),
                purpose="final_stream",
            ),
            scratchpad=scratchpad,
            quote_result=deps.quote_result_provider(),
            agent_session_id=state["agent_session_id"],
            token_usage_collector=deps.token_usage_collector,
            answer_delta_callback=deps.answer_delta_callback,
            psych_profile=state.get("psych_profile"),
        )
    else:
        answer = deps.answer_generator.answer_without_tools(
            content=state.get("plan_content", ""),
            quote_result=deps.quote_result_provider(),
            agent_session_id=state["agent_session_id"],
        )
    logger.info(
        "agent graph final stream done session_id=%s answer_len=%s",
        state["agent_session_id"],
        len(answer or ""),
    )
    return merge_state(state, answer=answer)


def _scratchpad_with_final_guard(state: AgentGraphState) -> list[object]:
    scratchpad = list(state.get("scratchpad", []))
    guard_message = _final_guard_message(state)
    if guard_message is not None:
        scratchpad.append(guard_message)
    return scratchpad


def _final_guard_message(state: AgentGraphState) -> object | None:
    decision = state.get("final_decision")
    stop_reason = state.get("stop_reason")
    if not _requires_final_guard(decision, stop_reason):
        return None
    content = (
        "最终回答约束：当前已经进入最终回答阶段，不能再调用任何工具。\n"
        f"前置判断结果：{getattr(decision, 'status', 'unknown')}；停止原因：{stop_reason or '证据不足'}。\n"
        "你必须只基于已有系统规则、用户问题、用户画像和工具结果回答。\n"
        "不得输出 DSML、tool_calls、invoke、JSON 工具调用片段或任何工具调用格式。\n"
        "如果证据不足，必须明确说明不足，只给观察条件、风险提示或保守结论，不能伪造确定结论。"
    )
    message = message_with_content(state.get("messages", []), content)
    if message is not None:
        return message
    planning_message = state.get("planning_message")
    if planning_message is None:
        return None
    try:
        return type(planning_message)(content=content)
    except Exception:
        return None


def _requires_final_guard(decision: FinalDecision | None, stop_reason: str | None) -> bool:
    if stop_reason in {"tool_budget_exhausted", "tool_all_failed", "final_backtrack_limit"}:
        return True
    return decision is not None and decision.status == "insufficient"
