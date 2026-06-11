from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.graph.profile_context import messages_with_memory_context, messages_with_psych_profile
from app.agent.graph.state import AgentGraphState, FinalDecision, merge_state, message_with_content

logger = logging.getLogger(__name__)


def final_decision_node(state: AgentGraphState) -> AgentGraphState:
    if not state.get("scratchpad") and not state.get("tool_result_cache"):
        return merge_state(state, final_decision=FinalDecision(status="ready", reason="无工具结果，直接回答。"))

    deps = state["deps"]
    decision_message = _message_with_fallback(state, _decision_prompt(state))
    if decision_message is None:
        return merge_state(state, final_decision=FinalDecision(status="ready", reason="无法构造判断消息。"))

    try:
        base_messages = messages_with_psych_profile(
            state["messages"],
            state.get("psych_profile"),
            purpose="final_decision",
        )
        base_messages = messages_with_memory_context(
            base_messages,
            state.get("memory_context"),
            purpose="final_decision",
        )
        response = deps.model.invoke([
            *base_messages,
            *state.get("scratchpad", []),
            decision_message,
        ])
        if deps.token_usage_collector:
            deps.token_usage_collector.add_message(response, "answer_readiness_check")
        decision = _parse_decision(getattr(response, "content", ""))
        updates: dict[str, Any] = {"final_decision": decision}
        if decision.status == "need_tool":
            block_reason = _need_tool_block_reason(state)
            if block_reason:
                blocked_decision = FinalDecision(
                    status="insufficient",
                    reason=_blocked_need_tool_reason(decision, block_reason),
                    planning_nudge=decision.planning_nudge,
                )
                _log_decision(state, blocked_decision)
                return merge_state(
                    state,
                    final_decision=blocked_decision,
                    stop_reason=block_reason,
                )
            updates["final_backtrack_count"] = int(state.get("final_backtrack_count", 0)) + 1
            if decision.planning_nudge:
                updates["planning_nudges"] = [*state.get("planning_nudges", []), decision.planning_nudge]
        _log_decision(state, decision)
        return merge_state(state, **updates)
    except Exception as exc:
        logger.warning("agent graph final decision failed session_id=%s error=%s", state["agent_session_id"], exc)
        return merge_state(state, final_decision=FinalDecision(status="ready", reason="判断失败，使用已有结果回答。"))


def _decision_prompt(state: AgentGraphState) -> str:
    evidence_summary = _evidence_summary(state.get("evidence_records", []))
    backtrack_count = int(state.get("final_backtrack_count", 0))
    return (
        "你现在只做最终回答前的证据充分性判断，不要输出给用户看的回答。\n"
        "如果已有工具结果足够回答，返回 JSON：{\"status\":\"ready\",\"reason\":\"...\"}。\n"
        "如果信息不足且还需要调用工具，返回 JSON："
        "{\"status\":\"need_tool\",\"reason\":\"...\",\"planningNudge\":\"...\"}。\n"
        "如果工具预算、超时或已回退后仍不足，返回 JSON：{\"status\":\"insufficient\",\"reason\":\"...\"}。\n"
        "只返回 JSON，不要返回 Markdown、DSML、tool_calls 或 invoke 片段。\n"
        f"已记录证据：{evidence_summary}\n"
        f"final 回退次数：{backtrack_count}\n"
        "不要要求重复查询已满足证据；只对缺失信息提出工具规划提示。"
    )


def _log_decision(state: AgentGraphState, decision: FinalDecision) -> None:
    logger.info(
        "agent graph final decision session_id=%s status=%s reason=%s",
        state["agent_session_id"],
        decision.status,
        decision.reason,
    )


def _message_with_fallback(state: AgentGraphState, content: str) -> Any | None:
    message = message_with_content(state["messages"], content)
    if message is not None:
        return message
    planning_message = state.get("planning_message")
    if planning_message is None:
        return None
    try:
        return type(planning_message)(content=content)
    except Exception:
        return None


def _evidence_summary(records: list[dict[str, Any]]) -> str:
    if not records:
        return "无"
    parts = []
    for record in records[-12:]:
        parts.append(
            f"{record.get('evidence_key')}[{record.get('scope_signature')}] via {record.get('source_tool')}"
        )
    return "；".join(parts)


def _parse_decision(content: Any) -> FinalDecision:
    text = str(content or "").strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:].strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        if _contains_tool_intent(text):
            return FinalDecision(
                status="need_tool",
                reason="判断输出包含工具意图，回到 planner 生成标准工具调用。",
                planning_nudge=_tool_intent_nudge(text),
            )
        return FinalDecision(status="ready", reason="判断输出不是 JSON，使用已有结果回答。")
    status = str(payload.get("status") or "ready")
    if status not in {"ready", "need_tool", "insufficient"}:
        status = "ready"
    return FinalDecision(
        status=status,  # type: ignore[arg-type]
        reason=str(payload.get("reason") or ""),
        planning_nudge=str(payload.get("planningNudge") or payload.get("planning_nudge") or ""),
    )


def _contains_tool_intent(text: str) -> bool:
    markers = ("tool_calls", "invoke", "knowledge_search", "market_quote", "market_kline", "scene_signal", "DSML")
    lower_text = text.lower()
    return any(marker.lower() in lower_text for marker in markers)


def _tool_intent_nudge(text: str) -> str:
    preview = text.replace("\n", " ")[:240]
    return f"final_decision 输出了工具意图但不是标准 JSON，请根据已有工具结果和缺口重新规划标准 tool_calls。工具意图摘要：{preview}"


def _need_tool_block_reason(state: AgentGraphState) -> str | None:
    stop_reason = state.get("stop_reason")
    if stop_reason in {"tool_budget_exhausted", "tool_all_failed"}:
        return str(stop_reason)
    if int(state.get("final_backtrack_count", 0)) + 1 > state["budget"].max_final_backtracks:
        return "final_backtrack_limit"
    if not state["budget"].step_allowed(int(state.get("step_index", 0))):
        return "tool_budget_exhausted"
    return None


def _blocked_need_tool_reason(decision: FinalDecision, block_reason: str) -> str:
    reason = decision.reason or "证据仍不足。"
    return f"{reason} 但已不能继续调用工具，原因：{block_reason}。"
