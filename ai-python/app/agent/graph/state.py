from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Literal, TypedDict

from app.agent.runtime.answer_generator import AgentAnswerGenerator
from app.agent.runtime.tool_call_budget import ToolCallBudget
from app.agent.runtime.tool_call_runner import ToolCallRunner
from app.agent.runtime.token_usage import AgentTokenUsageCollector

FinalDecisionStatus = Literal["ready", "need_tool", "insufficient"]
MemoryMode = Literal["reference", "continue_task"]


class EvidenceRecord(TypedDict):
    evidence_key: str
    scope_signature: str
    target_codes: list[str]
    params: dict[str, Any]
    source_tool: str
    cache_key: str


@dataclass(frozen=True)
class FinalDecision:
    status: FinalDecisionStatus
    reason: str = ""
    planning_nudge: str = ""


@dataclass(frozen=True)
class AgentGraphDeps:
    model: Any
    tools_by_name: dict[str, Any]
    tool_message_type: Any
    answer_generator: AgentAnswerGenerator
    tool_call_runner: ToolCallRunner
    quote_result_provider: Callable[[], dict[str, Any]]
    psych_profile_provider: Callable[[], dict[str, Any] | None] | None = None
    memory_provider: Callable[[str], str | None] | None = None
    token_usage_collector: AgentTokenUsageCollector | None = None
    answer_delta_callback: Callable[[str], None] | None = None


@dataclass(frozen=True)
class AgentGraphRunResult:
    answer: str | None


class AgentGraphState(TypedDict, total=False):
    messages: list[Any]
    scratchpad: list[Any]
    agent_session_id: str
    budget: ToolCallBudget
    deps: AgentGraphDeps
    step_index: int
    pending_tool_calls: list[dict[str, Any]]
    planning_message: Any
    plan_content: str
    planning_nudges: list[str]
    profile_required: bool
    psych_profile: dict[str, Any] | None
    memory_required: bool
    memory_mode: MemoryMode | None
    memory_context: str | None
    evidence_records: list[EvidenceRecord]
    tool_result_cache: dict[str, Any]
    final_decision: FinalDecision
    final_backtrack_count: int
    answer: str | None
    stop_reason: str | None


def initial_state(
    *,
    messages: list[Any],
    agent_session_id: str,
    deps: AgentGraphDeps,
    budget: ToolCallBudget,
) -> AgentGraphState:
    return {
        "messages": messages,
        "scratchpad": [],
        "agent_session_id": agent_session_id,
        "budget": budget,
        "deps": deps,
        "step_index": 0,
        "pending_tool_calls": [],
        "plan_content": "",
        "planning_nudges": [],
        "profile_required": False,
        "psych_profile": None,
        "memory_required": False,
        "memory_mode": None,
        "memory_context": None,
        "evidence_records": [],
        "tool_result_cache": {},
        "final_backtrack_count": 0,
        "answer": None,
        "stop_reason": None,
    }


def merge_state(state: AgentGraphState, **updates: Any) -> AgentGraphState:
    next_state = dict(state)
    next_state.update(updates)
    return next_state


def message_with_content(messages: list[Any], content: str) -> Any | None:
    if not messages:
        return None
    message_type = type(messages[0])
    try:
        return message_type(content=content)
    except Exception:
        return None
