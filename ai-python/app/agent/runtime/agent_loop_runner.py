from __future__ import annotations

import logging
from typing import Any

from app.agent.planning.agent_planner import AgentPlanner
from app.agent.runtime.agent_trace import AgentTrace
from app.agent.runtime.answer_generator import AgentAnswerGenerator
from app.agent.runtime.tool_call_budget import ToolCallBudget
from app.agent.runtime.tool_call_runner import ToolCallRunner

logger = logging.getLogger(__name__)


class AgentLoopRunner:
    def __init__(
        self,
        planner: AgentPlanner | None = None,
        tool_call_runner: ToolCallRunner | None = None,
        answer_generator: AgentAnswerGenerator | None = None,
    ) -> None:
        self._planner = planner or AgentPlanner()
        self._tool_call_runner = tool_call_runner or ToolCallRunner()
        self._answer_generator = answer_generator or AgentAnswerGenerator()

    def run(
        self,
        model: Any,
        messages: list[Any],
        tools_by_name: dict[str, Any],
        tool_message_type: Any,
        quote_result_provider: Any,
        agent_session_id: str,
        budget: ToolCallBudget | None = None,
    ) -> str | None:
        active_budget = budget or ToolCallBudget()
        trace = AgentTrace()
        scratchpad: list[Any] = []
        last_plan_content = ""

        for step_index in range(active_budget.max_steps):
            if not active_budget.step_allowed(step_index):
                logger.info("agent loop budget stopped before planning session_id=%s step=%s", agent_session_id, step_index)
                break

            plan = self._planner.plan(
                model=model,
                messages=[*messages, *scratchpad],
                tools=list(tools_by_name.values()),
                agent_session_id=agent_session_id,
            )
            last_plan_content = plan.content
            if not plan.standard_tool_calls:
                if plan.content:
                    return self._answer_generator.answer_without_tools(
                        content=plan.content,
                        quote_result=quote_result_provider(),
                        agent_session_id=agent_session_id,
                    )
                if scratchpad:
                    return self._answer_generator.answer_from_scratchpad(
                        model=model,
                        messages=messages,
                        scratchpad=scratchpad,
                        quote_result=quote_result_provider(),
                        agent_session_id=agent_session_id,
                    )
                return self._answer_generator.answer_without_tools(
                    content=plan.content,
                    quote_result=quote_result_provider(),
                    agent_session_id=agent_session_id,
                )

            trimmed_calls = active_budget.trim_tool_calls(plan.standard_tool_calls)
            if not trimmed_calls:
                logger.info("agent loop no tool calls left after budget trim session_id=%s step=%s", agent_session_id, step_index)
                break

            run_result = self._tool_call_runner.run_standard_tools(
                tool_calls=trimmed_calls,
                tools_by_name=tools_by_name,
                agent_session_id=agent_session_id,
                tool_message_type=tool_message_type,
                step_index=step_index,
            )
            trace.extend(run_result.trace_entries)
            if not run_result.tool_messages:
                logger.warning("agent loop step produced no tool messages session_id=%s step=%s", agent_session_id, step_index)
                break

            scratchpad.append(self._planning_message_with_tool_calls(plan.planning_message, trimmed_calls))
            scratchpad.extend(run_result.tool_messages)

            if run_result.all_failed:
                logger.warning("agent loop step all tool calls failed session_id=%s step=%s", agent_session_id, step_index)
                break
            if active_budget.timed_out():
                logger.info("agent loop timed out after tools session_id=%s step=%s", agent_session_id, step_index)
                break

        if scratchpad:
            return self._answer_generator.answer_from_scratchpad(
                model=model,
                messages=messages,
                scratchpad=scratchpad,
                quote_result=quote_result_provider(),
                agent_session_id=agent_session_id,
            )
        return self._answer_generator.answer_without_tools(
            content=last_plan_content,
            quote_result=quote_result_provider(),
            agent_session_id=agent_session_id,
        )

    def _planning_message_with_tool_calls(self, planning_message: Any, tool_calls: list[dict[str, Any]]) -> Any:
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
