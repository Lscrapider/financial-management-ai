from __future__ import annotations

import logging

from app.agent.graph.state import AgentGraphState, merge_state
from app.agent.prompts.final_answer_prompt_builder import FinalAnswerPromptBuilder

logger = logging.getLogger(__name__)

_final_prompt_builder = FinalAnswerPromptBuilder()


def final_stream_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    has_tool_evidence = bool(state.get("scratchpad") or state.get("tool_result_cache"))
    final_messages = _final_prompt_builder.build_messages(state) if has_tool_evidence else []
    decision = state.get("final_decision")
    logger.info(
        "agent graph final stream enter session_id=%s has_tool_evidence=%s decision_status=%s "
        "stop_reason=%s has_delta_callback=%s",
        state["agent_session_id"],
        has_tool_evidence,
        getattr(decision, "status", None),
        state.get("stop_reason"),
        deps.answer_delta_callback is not None,
    )
    if has_tool_evidence:
        answer = deps.answer_generator.answer_from_messages(
            model=deps.model,
            messages=final_messages,
            quote_result=deps.quote_result_provider(),
            agent_session_id=state["agent_session_id"],
            token_usage_collector=deps.token_usage_collector,
            answer_delta_callback=deps.answer_delta_callback,
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
