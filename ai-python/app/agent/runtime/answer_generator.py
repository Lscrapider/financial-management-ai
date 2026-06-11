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
        psych_profile: dict[str, Any] | None = None,
    ) -> str | None:
        if not tool_messages:
            logger.warning("langchain tool calls did not produce tool messages")
            return None
        logger.info(
            "agent langchain final answer start session_id=%s tool_protocol=standard tool_messages=%s",
            agent_session_id,
            len(tool_messages),
        )
        final_message = model.invoke([
            *self._messages_with_psych_profile(messages, psych_profile),
            planning_message,
            *tool_messages,
        ])
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
        psych_profile: dict[str, Any] | None = None,
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
            messages=[*self._messages_with_psych_profile(messages, psych_profile), *scratchpad],
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

    def _messages_with_psych_profile(
        self,
        messages: list[Any],
        psych_profile: dict[str, Any] | None,
    ) -> list[Any]:
        if not psych_profile:
            return messages
        message_type = type(messages[0]) if messages else None
        if message_type is None:
            return messages
        try:
            return [
                *messages,
                message_type(content=self._psych_profile_prompt(psych_profile)),
            ]
        except Exception:
            logger.warning("agent psych profile prompt message build failed")
            return messages

    def _psych_profile_prompt(self, profile: dict[str, Any]) -> str:
        holding_mindset = profile.get("holdingMindset")
        if isinstance(holding_mindset, list):
            holding_mindset_text = "、".join(str(item) for item in holding_mindset)
        else:
            holding_mindset_text = str(holding_mindset or "未识别")
        advice_style = str(profile.get("adviceStyle") or "conditional_trade")
        rules = [
            "不要因为用户心理画像而改变行情事实；画像只影响建议表达、仓位边界和风险提醒。",
            "只在工具结果或报告上下文支持时给买卖建议。",
            "买入相关建议必须包含触发条件、仓位边界和退出条件。",
        ]
        if advice_style == "risk_first":
            rules.append("当前建议强度为 risk_first，只能给风险提示和观察条件，不能直接给买入仓位。")
        elif advice_style == "conditional_trade":
            rules.append("当前建议强度为 conditional_trade，只能给条件建议，不能直接说现在买入固定仓位。")
        elif advice_style == "explicit_trade_light_position":
            rules.append("当前建议强度为 explicit_trade_light_position，可以给明确买卖，但仓位必须限制在轻仓试错区间。")
        if "chase_high_tendency" in holding_mindset_text:
            rules.append("用户有追高倾向，必须提醒不要追入式买入。")
        if "hard_to_stop_loss" in holding_mindset_text:
            rules.append("用户止损容易犹豫，必须把退出条件放在建议主体里。")
        return (
            "用户投资心理画像（仅用于最终回答，不用于改变客观数据判断）：\n"
            f"- 波动情绪：{profile.get('riskEmotion') or '未识别'}\n"
            f"- 决策风格：{profile.get('decisionStyle') or '未识别'}\n"
            f"- 持仓心态：{holding_mindset_text}\n"
            f"- 操作节奏：{profile.get('tradingTempo') or '未识别'}\n"
            f"- 信息偏好：{profile.get('explanationPreference') or '未识别'}\n"
            f"- 建议强度：{advice_style}\n"
            f"- 画像摘要：{profile.get('summary') or '无'}\n\n"
            "回答规则：\n"
            + "\n".join(f"{index}. {rule}" for index, rule in enumerate(rules, start=1))
        )

    def _should_flush_delta(self, delta: str) -> bool:
        return len(delta) >= STREAM_DELTA_MIN_CHARS or delta.endswith(STREAM_DELTA_FLUSH_SUFFIXES)

    def _send_delta(self, answer_delta_callback: Callable[[str], None], delta: str) -> None:
        try:
            answer_delta_callback(delta)
        except Exception as exc:
            logger.warning("agent answer delta callback failed: %s", exc)
