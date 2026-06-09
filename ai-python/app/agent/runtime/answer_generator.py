from __future__ import annotations

import logging
from typing import Any

from app.agent.services.answer_builder import AgentAnswerBuilder

logger = logging.getLogger(__name__)


class AgentAnswerGenerator:
    def __init__(self, answer_builder: AgentAnswerBuilder | None = None) -> None:
        self._answer_builder = answer_builder or AgentAnswerBuilder()

    def answer_with_standard_tools(
        self,
        model: Any,
        messages: list[Any],
        planning_message: Any,
        tool_messages: list[Any],
        quote_result: dict[str, Any],
        agent_session_id: str,
    ) -> str | None:
        if not tool_messages:
            logger.warning("langchain tool calls did not produce tool messages")
            return None
        logger.info(
            "agent langchain final answer start session_id=%s tool_protocol=standard tool_messages=%s",
            agent_session_id,
            len(tool_messages),
        )
        final_message = model.invoke([*messages, planning_message, *tool_messages])
        return self._extract_final_answer(final_message, agent_session_id, "standard", quote_result)

    def answer_without_tools(self, content: str, quote_result: dict[str, Any], agent_session_id: str) -> str | None:
        if not content:
            logger.warning("langchain did not request tool and returned empty content")
            return None
        logger.warning(
            "langchain did not request tool, model content will be returned session_id=%s",
            agent_session_id,
        )
        return self._answer_builder.answer_or_fallback(content, quote_result)

    def _extract_final_answer(
        self,
        final_message: Any,
        agent_session_id: str,
        tool_protocol: str,
        quote_result: dict[str, Any],
    ) -> str:
        content = getattr(final_message, "content", "")
        logger.info(
            "agent langchain final answer done session_id=%s tool_protocol=%s answer_len=%s",
            agent_session_id,
            tool_protocol,
            len(str(content or "")),
        )
        if content:
            return self._answer_builder.answer_or_fallback(str(content), quote_result)
        return self._answer_builder.fallback_answer(quote_result)
