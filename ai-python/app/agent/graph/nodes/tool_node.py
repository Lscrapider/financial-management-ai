from __future__ import annotations

import logging
from typing import Any

from app.agent.graph.state import AgentGraphState, EvidenceRecord, merge_state

logger = logging.getLogger(__name__)


def tool_node(state: AgentGraphState) -> AgentGraphState:
    deps = state["deps"]
    budget = state["budget"]
    step_index = int(state.get("step_index", 0))
    requested_calls = list(state.get("pending_tool_calls") or [])
    cached_messages: list[Any] = []
    cached_calls: list[dict[str, Any]] = []
    real_calls: list[dict[str, Any]] = []
    tool_result_cache = dict(state.get("tool_result_cache") or {})

    for tool_call in requested_calls:
        signature = budget.signature(tool_call)
        cached_result = tool_result_cache.get(signature)
        if cached_result is not None:
            cached_calls.append(tool_call)
            cached_messages.append(deps.tool_message_type(
                content=cached_result,
                tool_call_id=str(tool_call.get("id") or ""),
            ))
            logger.info(
                "agent graph tool cache hit session_id=%s step=%s tool_name=%s",
                state["agent_session_id"],
                step_index,
                tool_call.get("name"),
            )
            continue
        real_calls.append(tool_call)

    trimmed_calls = budget.trim_tool_calls(real_calls)
    if not trimmed_calls and not cached_messages:
        logger.info(
            "agent graph no tool calls after budget session_id=%s step=%s",
            state["agent_session_id"],
            step_index,
        )
        return merge_state(
            state,
            pending_tool_calls=[],
            stop_reason="tool_budget_exhausted",
        )

    tool_messages = [*cached_messages]
    all_failed = False
    if trimmed_calls:
        run_result = deps.tool_call_runner.run_standard_tools(
            tool_calls=trimmed_calls,
            tools_by_name=deps.tools_by_name,
            agent_session_id=state["agent_session_id"],
            tool_message_type=deps.tool_message_type,
            step_index=step_index,
        )
        tool_messages.extend(run_result.tool_messages)
        all_failed = run_result.all_failed
        for tool_call, tool_message in zip(trimmed_calls, run_result.tool_messages, strict=False):
            signature = budget.signature(tool_call)
            content = getattr(tool_message, "content", "")
            tool_result_cache[signature] = content

    scratchpad = list(state.get("scratchpad") or [])
    planning_message = _planning_message_with_tool_calls(state.get("planning_message"), [*cached_calls, *trimmed_calls])
    if planning_message is not None:
        scratchpad.append(planning_message)
    scratchpad.extend(tool_messages)

    evidence_records = [
        *list(state.get("evidence_records") or []),
        *[_evidence_record(tool_call, budget.signature(tool_call)) for tool_call in trimmed_calls],
    ]
    return merge_state(
        state,
        scratchpad=scratchpad,
        pending_tool_calls=[],
        tool_result_cache=tool_result_cache,
        evidence_records=evidence_records,
        step_index=step_index + 1,
        stop_reason="tool_all_failed" if all_failed else state.get("stop_reason"),
    )


def _evidence_record(tool_call: dict[str, Any], cache_key: str) -> EvidenceRecord:
    args = tool_call.get("args") if isinstance(tool_call.get("args"), dict) else {}
    tool_name = str(tool_call.get("name") or "")
    target_codes = _target_codes(args)
    return {
        "evidence_key": _evidence_key(tool_name),
        "scope_signature": _scope_signature(tool_name, target_codes, args),
        "target_codes": target_codes,
        "params": args,
        "source_tool": tool_name,
        "cache_key": cache_key,
    }


def _target_codes(args: dict[str, Any]) -> list[str]:
    value = args.get("target_codes") or args.get("target_code")
    if isinstance(value, list):
        return sorted({str(item).strip().upper() for item in value if str(item).strip()})
    if value:
        return [str(value).strip().upper()]
    return []


def _evidence_key(tool_name: str) -> str:
    mapping = {
        "market_quote": "market_quote",
        "market_kline_trend": "kline_trend",
        "market_intraday_summary": "intraday",
        "scene_signal_context": "scene_signal",
        "stock_fundamental_context": "fundamental",
        "knowledge_search": "knowledge",
    }
    return mapping.get(tool_name, tool_name)


def _scope_signature(tool_name: str, target_codes: list[str], args: dict[str, Any]) -> str:
    period = args.get("period") or args.get("kline_period") or args.get("freq") or ""
    window = args.get("window") or args.get("limit") or args.get("days") or ""
    return "|".join([tool_name, ",".join(target_codes), str(period), str(window)])


def _planning_message_with_tool_calls(planning_message: Any, tool_calls: list[dict[str, Any]]) -> Any:
    if planning_message is None:
        return None
    original_tool_calls = getattr(planning_message, "tool_calls", []) or []
    if len(original_tool_calls) == len(tool_calls):
        return planning_message

    allowed_ids = {str(tool_call.get("id") or "") for tool_call in tool_calls}
    additional_kwargs = dict(getattr(planning_message, "additional_kwargs", {}) or {})
    raw_tool_calls = additional_kwargs.get("tool_calls")
    if isinstance(raw_tool_calls, list):
        additional_kwargs["tool_calls"] = [
            raw_tool_call
            for raw_tool_call in raw_tool_calls
            if str(raw_tool_call.get("id") or "") in allowed_ids
        ]

    update = {
        "tool_calls": tool_calls,
        "additional_kwargs": additional_kwargs,
    }
    if hasattr(planning_message, "model_copy"):
        return planning_message.model_copy(update=update)
    if hasattr(planning_message, "copy"):
        return planning_message.copy(update=update)
    return planning_message
