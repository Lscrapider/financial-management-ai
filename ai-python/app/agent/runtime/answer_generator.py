from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from app.agent.runtime.token_usage import AgentTokenUsageCollector
from app.agent.services.answer_builder import AgentAnswerBuilder

logger = logging.getLogger(__name__)

STREAM_DELTA_MIN_CHARS = 24
STREAM_DELTA_FLUSH_SUFFIXES = ("。", "！", "？", "；", "\n")


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
        token_usage_collector: AgentTokenUsageCollector | None = None,
        answer_delta_callback: Callable[[str], None] | None = None,
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
        if token_usage_collector:
            token_usage_collector.add_message(final_message, "final_answer")
        return self._extract_final_answer(final_message, agent_session_id, "standard", quote_result)

    def answer_from_scratchpad(
        self,
        model: Any,
        messages: list[Any],
        scratchpad: list[Any],
        quote_result: dict[str, Any],
        agent_session_id: str,
        token_usage_collector: AgentTokenUsageCollector | None = None,
        answer_delta_callback: Callable[[str], None] | None = None,
    ) -> str | None:
        if not scratchpad:
            logger.warning("langchain scratchpad is empty")
            return None
        logger.info(
            "agent langchain final answer start session_id=%s tool_protocol=loop scratchpad_messages=%s",
            agent_session_id,
            len(scratchpad),
        )
        final_message = self._final_message(
            model=model,
            messages=[*messages, *scratchpad],
            answer_delta_callback=answer_delta_callback,
        )
        if token_usage_collector:
            token_usage_collector.add_message(final_message, "final_answer")
        return self._extract_final_answer(final_message, agent_session_id, "loop", quote_result)

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

    def _final_message(
        self,
        model: Any,
        messages: list[Any],
        answer_delta_callback: Callable[[str], None] | None,
    ) -> Any:
        if answer_delta_callback is None or not hasattr(model, "stream"):
            return model.invoke(messages)

        final_message = None
        pending_delta = ""
        for chunk in model.stream(messages):
            final_message = chunk if final_message is None else final_message + chunk
            content = getattr(chunk, "content", "") or ""
            if not isinstance(content, str) or not content:
                continue
            pending_delta += content
            if self._should_flush_delta(pending_delta):
                self._send_delta(answer_delta_callback, pending_delta)
                pending_delta = ""
        if pending_delta:
            self._send_delta(answer_delta_callback, pending_delta)
        return final_message if final_message is not None else model.invoke(messages)

    def _should_flush_delta(self, delta: str) -> bool:
        return len(delta) >= STREAM_DELTA_MIN_CHARS or delta.endswith(STREAM_DELTA_FLUSH_SUFFIXES)

    def _send_delta(self, answer_delta_callback: Callable[[str], None], delta: str) -> None:
        try:
            answer_delta_callback(delta)
        except Exception as exc:
            logger.warning("agent answer delta callback failed: %s", exc)
