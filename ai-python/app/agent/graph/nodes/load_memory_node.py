from __future__ import annotations

import logging

from app.agent.graph.state import AgentGraphState, merge_state

logger = logging.getLogger(__name__)


def load_memory_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    provider = deps.memory_provider
    mode = str(state.get("memory_mode") or "reference")
    if provider is None:
        return merge_state(state, memory_context=None)
    try:
        memory_context = provider(mode)
        if memory_context:
            logger.info("agent graph memory loaded session_id=%s mode=%s", state["agent_session_id"], mode)
        return merge_state(state, memory_context=memory_context or None)
    except Exception as exc:
        logger.warning(
            "agent graph memory load failed session_id=%s mode=%s error=%s",
            state["agent_session_id"],
            mode,
            exc,
        )
        return merge_state(state, memory_context=None)
