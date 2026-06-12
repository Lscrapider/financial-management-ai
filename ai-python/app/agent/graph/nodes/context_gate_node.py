from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.graph.intent_policy import TRADE_ADVICE_KEYWORDS, classify_query_intent
from app.agent.graph.state import AgentGraphState, merge_state, message_with_content

logger = logging.getLogger(__name__)

DEFAULT_FALLBACK_PROFILE_REQUIRED = True
DEFAULT_FALLBACK_MEMORY_MODE = "continue_task"

MEMORY_REFERENCE_KEYWORDS = (
    "这个股票",
    "这只股票",
    "这个票",
    "这只票",
    "它",
    "现在说的不是",
    "说的不是",
    "刚才那个",
    "上一个",
    "刚才说的",
    "刚才提到",
    "基于刚才",
    "上一轮",
    "历史偏好",
)

MEMORY_CONTINUE_KEYWORDS = (
    "继续",
    "具体分析下",
    "展开说说",
    "解释呢",
    "解释下",
    "解释一下",
    "为什么",
    "依据呢",
    "理由呢",
    "那怎么办",
    "怎么办",
    "接下来呢",
    "所以呢",
    "怎么看",
    "上面那个报告",
    "刚才的结论",
    "继续分析",
    "接着说",
)

MEMORY_SKIP_KEYWORDS = (
    "重新分析",
    "换一个",
    "换个",
    "全市场",
    "全部股票",
    "从全部股票",
)


def context_gate_node(state: AgentGraphState) -> AgentGraphState:
    user_text = _message_text(state.get("messages") or [])
    normalized_text = user_text.lower()
    intent_policy = classify_query_intent(user_text)
    effective_budget = intent_policy.apply_budget(state["budget"])
    rule_profile_required = _requires_profile(normalized_text)
    memory_skip = _skips_memory(normalized_text)
    rule_memory_mode = None if memory_skip else _memory_mode(normalized_text)
    fallback_profile_required = False
    fallback_memory_mode: str | None = None
    if _needs_llm_fallback(rule_profile_required, rule_memory_mode, memory_skip):
        fallback_profile_required, fallback_memory_mode = _llm_fallback_decision(state, user_text)
    profile_required = rule_profile_required or fallback_profile_required
    memory_mode = None if memory_skip else rule_memory_mode or fallback_memory_mode
    logger.info(
        "agent graph context gate session_id=%s profile_required=%s memory_required=%s query_depth=%s tool_count=%s",
        state["agent_session_id"],
        profile_required,
        memory_mode is not None,
        intent_policy.query_depth,
        len(intent_policy.allowed_tools),
    )
    return merge_state(
        state,
        budget=effective_budget,
        query_depth=intent_policy.query_depth,
        allowed_tool_names=list(intent_policy.allowed_tools),
        planner_intent_hint=intent_policy.planner_hint,
        profile_required=profile_required,
        memory_required=memory_mode is not None,
        memory_mode=memory_mode,
    )


def _message_text(messages: list[Any]) -> str:
    if not messages:
        return ""
    content = getattr(messages[-1], "content", "")
    return content if isinstance(content, str) else ""


def _requires_profile(text: str) -> bool:
    return any(keyword.lower() in text for keyword in TRADE_ADVICE_KEYWORDS)


def _skips_memory(text: str) -> bool:
    if any(keyword in text for keyword in MEMORY_SKIP_KEYWORDS):
        return True
    return False


def _memory_mode(text: str) -> str | None:
    if any(keyword in text for keyword in MEMORY_CONTINUE_KEYWORDS):
        return "continue_task"
    if any(keyword in text for keyword in MEMORY_REFERENCE_KEYWORDS):
        return "reference"
    return None


def _needs_llm_fallback(rule_profile_required: bool, rule_memory_mode: str | None, memory_skip: bool) -> bool:
    if not rule_profile_required:
        return True
    return rule_memory_mode is None and not memory_skip


def _llm_fallback_decision(state: AgentGraphState, user_text: str) -> tuple[bool, str | None]:
    if not user_text.strip():
        return False, None
    prompt_message = message_with_content(state.get("messages", []), _fallback_prompt(user_text))
    if prompt_message is None:
        return _default_fallback_decision()
    try:
        response = state["deps"].model.invoke([prompt_message])
        collector = state["deps"].token_usage_collector
        if collector is not None:
            collector.add_message(response, "context_gate")
        return _parse_fallback_response(getattr(response, "content", ""))
    except Exception as exc:
        logger.warning(
            "agent graph context gate fallback failed session_id=%s error=%s",
            state["agent_session_id"],
            exc,
        )
        return _default_fallback_decision()


def _fallback_prompt(user_text: str) -> str:
    return (
        "你只做 AI Chat 上下文门控判断，不回答用户，不调用工具。\n"
        "根据当前用户问题判断是否需要用户画像和短期记忆。\n"
        "用户画像用于明确买卖、仓位、止盈止损、加仓减仓、操作建议等问题。\n"
        "短期记忆用于代词指代、短追问、或延续上一轮任务，"
        "例如：然后呢、解释呢、那我能不能买呢、那怎么办、接下来呢、所以呢、怎么看。\n"
        "如果问题明确是新任务、新标的、重新分析、换一个、全市场筛选，则 memoryRequired=false。\n"
        "memoryMode 只能是 reference、continue_task 或 null；短追问通常用 continue_task，代词指代通常用 reference。\n"
        "只返回 JSON，格式："
        "{\"profileRequired\":false,\"memoryRequired\":false,\"memoryMode\":null,\"reason\":\"...\"}\n"
        f"当前用户问题：{user_text}"
    )


def _parse_fallback_response(content: Any) -> tuple[bool, str | None]:
    text = str(content or "").strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:].strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return _default_fallback_decision()
    profile_required = _as_bool(payload.get("profileRequired", payload.get("profile_required")))
    memory_required = _as_bool(payload.get("memoryRequired", payload.get("memory_required")))
    memory_mode = str(payload.get("memoryMode") or payload.get("memory_mode") or "").strip()
    if not memory_required:
        return profile_required, None
    if memory_mode not in {"reference", "continue_task"}:
        memory_mode = "continue_task"
    return profile_required, memory_mode


def _default_fallback_decision() -> tuple[bool, str]:
    return DEFAULT_FALLBACK_PROFILE_REQUIRED, DEFAULT_FALLBACK_MEMORY_MODE


def _as_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"true", "1", "yes"}
    return bool(value)
