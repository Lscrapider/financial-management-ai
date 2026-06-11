from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)

DEFAULT_PSYCH_PROFILE = {
    "adviceStyle": "no_trade_advice",
    "summary": "未读取到可用用户画像，默认不生成明确买卖建议。",
}


def messages_with_psych_profile(
    messages: list[Any],
    psych_profile: dict[str, Any] | None,
    *,
    purpose: str,
) -> list[Any]:
    if not psych_profile or not messages:
        return messages
    try:
        return [
            *messages,
            type(messages[0])(content=_psych_profile_context(psych_profile, purpose)),
        ]
    except Exception:
        logger.warning("agent graph psych profile context message build failed purpose=%s", purpose)
        return messages


def messages_with_memory_context(
    messages: list[Any],
    memory_context: str | None,
    *,
    purpose: str,
) -> list[Any]:
    if not memory_context or not messages:
        return messages
    try:
        return [
            *messages,
            type(messages[0])(content=_memory_context(memory_context, purpose)),
        ]
    except Exception:
        logger.warning("agent graph memory context message build failed purpose=%s", purpose)
        return messages


def _psych_profile_context(profile: dict[str, Any], purpose: str) -> str:
    advice_style = str(profile.get("adviceStyle") or "no_trade_advice")
    summary = str(profile.get("summary") or "无")
    if purpose == "planner":
        return (
            "用户投资心理画像（仅用于工具规划，不用于改变客观行情事实）：\n"
            f"- 建议强度：{advice_style}\n"
            f"- 画像摘要：{summary}\n"
            "如果用户请求买卖、仓位、止盈止损或操作建议，规划工具时优先补齐行情、K线、场景信号和知识库风险策略依据。"
        )
    return (
        "用户投资心理画像（用于最终回答前判断和建议边界，不用于改变客观行情事实）：\n"
        f"- 建议强度：{advice_style}\n"
        f"- 画像摘要：{summary}\n"
        "如果建议强度为 no_trade_advice，不能把缺少画像当作需要补工具，只能进入非明确交易建议型回答。"
    )


def _memory_context(memory_context: str, purpose: str) -> str:
    if purpose == "planner":
        return (
            "当前会话短期记忆（仅用于消解当前用户问题中的指代或延续上一轮任务）：\n"
            f"{memory_context}\n"
            "如果当前问题已明确新标的或新任务，不要因为短期记忆里的标的自动延续。"
        )
    return (
        "当前会话短期记忆（仅用于理解当前问题和回答衔接，不替代本轮工具结果）：\n"
        f"{memory_context}"
    )
