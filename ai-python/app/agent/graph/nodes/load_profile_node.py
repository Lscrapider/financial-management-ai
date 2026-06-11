from __future__ import annotations

import logging

from app.agent.graph.profile_context import DEFAULT_PSYCH_PROFILE
from app.agent.graph.state import AgentGraphState, merge_state

logger = logging.getLogger(__name__)


def load_profile_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    provider = deps.psych_profile_provider
    if provider is None:
        return merge_state(state, psych_profile=DEFAULT_PSYCH_PROFILE)
    try:
        profile = provider()
        if profile:
            logger.info("agent graph psych profile loaded session_id=%s", state["agent_session_id"])
            return merge_state(state, psych_profile=profile)
        return merge_state(state, psych_profile=DEFAULT_PSYCH_PROFILE)
    except Exception as exc:
        logger.warning(
            "agent graph psych profile load failed session_id=%s error=%s",
            state["agent_session_id"],
            exc,
        )
        return merge_state(state, psych_profile=DEFAULT_PSYCH_PROFILE)
