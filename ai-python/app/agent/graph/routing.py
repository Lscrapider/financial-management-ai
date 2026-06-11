from __future__ import annotations

from app.agent.graph.state import AgentGraphState

MAX_FINAL_BACKTRACKS = 2


def route_after_context_gate(state: AgentGraphState) -> str:
    if state.get("profile_required"):
        return "load_profile"
    if state.get("memory_required"):
        return "load_memory"
    return "planner"


def route_after_load_profile(state: AgentGraphState) -> str:
    return "load_memory" if state.get("memory_required") else "planner"


def route_after_planner(state: AgentGraphState) -> str:
    return "tools" if state.get("pending_tool_calls") else "final_decision"


def route_after_tools(state: AgentGraphState) -> str:
    budget = state["budget"]
    if state.get("stop_reason") in {"tool_budget_exhausted", "tool_all_failed"}:
        return "final_decision"
    if not budget.step_allowed(int(state.get("step_index", 0))):
        return "final_decision"
    return "planner"


def route_after_final_decision(state: AgentGraphState) -> str:
    decision = state.get("final_decision")
    if decision is None or decision.status != "need_tool":
        return "final_stream"
    if int(state.get("final_backtrack_count", 0)) > MAX_FINAL_BACKTRACKS:
        return "final_stream"
    if not state["budget"].step_allowed(int(state.get("step_index", 0))):
        return "final_stream"
    return "planner"
