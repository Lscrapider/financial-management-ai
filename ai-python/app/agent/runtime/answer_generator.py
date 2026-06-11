from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from app.agent.runtime.token_usage import AgentTokenUsageCollector
from app.agent.services.answer_builder import AgentAnswerBuilder

logger = logging.getLogger(__name__)

STREAM_DELTA_MIN_CHARS = 24
STREAM_DELTA_FLUSH_SUFFIXES = ("。", "！", "？", "；", "\n")
FINAL_ANSWER_MAX_ATTEMPTS = 3
FINAL_ANSWER_UNAVAILABLE_MESSAGE = "AI 服务暂时不可用，请稍后重试。"
TOOL_CALL_MARKERS = (
    "DSML",
    "tool_calls",
    "invoke name=",
    "<invoke",
    "</invoke>",
)


class AgentAnswerGenerator:
    def __init__(self, answer_builder: AgentAnswerBuilder | None = None) -> None:
        self._answer_builder = answer_builder or AgentAnswerBuilder()

    def answer_from_messages(
        self,
        model: Any,
        messages: list[Any],
        quote_result: dict[str, Any],
        agent_session_id: str,
        token_usage_collector: AgentTokenUsageCollector | None = None,
        answer_delta_callback: Callable[[str], None] | None = None,
    ) -> str | None:
        if not messages:
            logger.warning("agent final answer messages are empty session_id=%s", agent_session_id)
            return FINAL_ANSWER_UNAVAILABLE_MESSAGE
        logger.info(
            "agent langchain final answer start session_id=%s message_count=%s",
            agent_session_id,
            len(messages),
        )
        attempt_messages = list(messages)
        for attempt in range(1, FINAL_ANSWER_MAX_ATTEMPTS + 1):
            try:
                final_message = self._final_message(
                    model=model,
                    messages=attempt_messages,
                    answer_delta_callback=answer_delta_callback,
                    agent_session_id=agent_session_id,
                    attempt=attempt,
                )
            except Exception as exc:
                logger.warning(
                    "agent final answer stream failed session_id=%s attempt=%s error=%s",
                    agent_session_id,
                    attempt,
                    exc,
                )
                final_message = None
            if token_usage_collector and final_message is not None:
                token_usage_collector.add_message(final_message, "final_answer")

            content = getattr(final_message, "content", "") if final_message is not None else ""
            answer = self._answer_builder.answer_or_fallback(str(content), quote_result) if content else ""
            if answer and not self._contains_tool_call_markup(answer):
                logger.info(
                    "agent langchain final answer done session_id=%s answer_len=%s attempt=%s",
                    agent_session_id,
                    len(answer),
                    attempt,
                )
                return answer

            logger.warning(
                "agent final answer invalid session_id=%s attempt=%s reason=%s",
                agent_session_id,
                attempt,
                "tool_markup" if self._contains_tool_call_markup(answer) else "empty_content",
            )
            attempt_messages = self._retry_messages(messages, attempt)

        logger.warning("agent final answer unavailable after retries session_id=%s", agent_session_id)
        return FINAL_ANSWER_UNAVAILABLE_MESSAGE

    def answer_without_tools(self, content: str, quote_result: dict[str, Any], agent_session_id: str) -> str | None:
        if not content:
            logger.warning("langchain did not request tool and returned empty content")
            return None
        logger.warning(
            "langchain did not request tool, model content will be returned session_id=%s",
            agent_session_id,
        )
        return self._answer_builder.answer_or_fallback(content, quote_result)

    def _final_message(
        self,
        model: Any,
        messages: list[Any],
        answer_delta_callback: Callable[[str], None] | None,
        agent_session_id: str,
        attempt: int,
    ) -> Any:
        if answer_delta_callback is None:
            logger.debug(
                "agent answer stream disabled session_id=%s attempt=%s reason=no_delta_callback",
                agent_session_id,
                attempt,
            )
            return model.invoke(messages)
        if not hasattr(model, "stream"):
            logger.debug(
                "agent answer stream disabled session_id=%s attempt=%s reason=model_without_stream",
                agent_session_id,
                attempt,
            )
            return model.invoke(messages)

        final_message = None
        pending_delta = ""
        suppress_delta = False
        sent_delta_count = 0
        sent_delta_chars = 0
        for chunk in model.stream(messages):
            final_message = chunk if final_message is None else final_message + chunk
            content = getattr(chunk, "content", "") or ""
            if not isinstance(content, str) or not content:
                continue
            if suppress_delta:
                continue
            pending_delta += content
            if self._contains_tool_call_markup(pending_delta):
                logger.warning(
                    "agent answer stream suppressed tool markup delta session_id=%s attempt=%s",
                    agent_session_id,
                    attempt,
                )
                pending_delta = ""
                suppress_delta = True
                continue
            if self._should_flush_delta(pending_delta):
                self._send_delta(answer_delta_callback, pending_delta)
                sent_delta_count += 1
                sent_delta_chars += len(pending_delta)
                pending_delta = ""
        if pending_delta:
            self._send_delta(answer_delta_callback, pending_delta)
            sent_delta_count += 1
            sent_delta_chars += len(pending_delta)
        logger.info(
            "agent answer stream done session_id=%s attempt=%s sent_delta_count=%s "
            "sent_delta_chars=%s suppress_delta=%s final_message_present=%s",
            agent_session_id,
            attempt,
            sent_delta_count,
            sent_delta_chars,
            suppress_delta,
            final_message is not None,
        )
        return final_message

    def _retry_messages(self, messages: list[Any], attempt: int) -> list[Any]:
        retry_message = self._message_with_content(
            messages,
            "上一次最终回答生成失败。现在重新生成最终回答，只能输出中文自然语言正文。"
            "不得调用工具，不得输出 DSML、tool_calls、invoke、JSON 工具调用片段。"
            "如果证据不足，就明确说明不足，并基于已有证据给出保守结论。"
            f"这是第 {attempt + 1} 次尝试。",
        )
        return [*messages, retry_message] if retry_message is not None else messages

    def _should_flush_delta(self, delta: str) -> bool:
        return len(delta) >= STREAM_DELTA_MIN_CHARS or delta.endswith(STREAM_DELTA_FLUSH_SUFFIXES)

    def _send_delta(self, answer_delta_callback: Callable[[str], None], delta: str) -> None:
        try:
            answer_delta_callback(delta)
        except Exception as exc:
            logger.warning("agent answer delta callback failed: %s", exc)

    def _contains_tool_call_markup(self, content: Any) -> bool:
        text = str(content or "")
        lower_text = text.lower()
        return any(marker.lower() in lower_text for marker in TOOL_CALL_MARKERS)

    def _message_with_content(self, messages: list[Any], content: str) -> Any | None:
        if not messages:
            return None
        try:
            return type(messages[-1])(content=content)
        except Exception:
            logger.warning("agent final answer retry message build failed")
            return None
