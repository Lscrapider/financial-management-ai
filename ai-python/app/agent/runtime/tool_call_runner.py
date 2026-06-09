from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from app.agent.runtime.agent_trace import AgentToolTraceEntry

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ToolCallRunResult:
    tool_messages: list[Any]
    trace_entries: list[AgentToolTraceEntry]

    @property
    def all_failed(self) -> bool:
        return bool(self.trace_entries) and all(not entry.success for entry in self.trace_entries)


class ToolCallRunner:
    def run_standard_tools(
        self,
        tool_calls: list[dict[str, Any]],
        tools_by_name: dict[str, Any],
        agent_session_id: str,
        tool_message_type: Any,
        step_index: int = 0,
    ) -> ToolCallRunResult:
        tool_messages = []
        trace_entries: list[AgentToolTraceEntry] = []
        for tool_call in tool_calls:
            tool_name = str(tool_call.get("name") or "")
            tool_call_id = str(tool_call.get("id") or "")
            tool_args_value = tool_call.get("args") or {}
            tool_args = tool_args_value if isinstance(tool_args_value, dict) else {}
            tool = tools_by_name.get(tool_name)
            if tool is None:
                logger.info(
                    "agent langchain skip unsupported tool session_id=%s tool_name=%s",
                    agent_session_id,
                    tool_name,
                )
                result = "不支持的工具调用，已跳过。"
                trace_entries.append(
                    self._trace_entry(
                        step_index=step_index,
                        tool_call_id=tool_call_id,
                        tool_name=tool_name,
                        args=tool_args,
                        success=False,
                        result=result,
                    )
                )
                if tool_call_id:
                    tool_messages.append(tool_message_type(content=result, tool_call_id=tool_call_id))
                continue
            logger.info(
                "agent langchain standard tool call session_id=%s tool_id=%s tool_name=%s args=%s",
                agent_session_id,
                tool_call_id,
                tool_name,
                tool_args,
            )
            try:
                tool_result = tool.invoke(tool_args)
                success = True
            except Exception as exc:
                logger.warning(
                    "agent langchain standard tool call failed session_id=%s tool_id=%s tool_name=%s error=%s",
                    agent_session_id,
                    tool_call_id,
                    tool_name,
                    exc,
                )
                tool_result = f"工具 {tool_name} 调用失败：{exc}"
                success = False
            trace_entries.append(
                self._trace_entry(
                    step_index=step_index,
                    tool_call_id=tool_call_id,
                    tool_name=tool_name,
                    args=tool_args,
                    success=success,
                    result=tool_result,
                )
            )
            tool_messages.append(tool_message_type(content=tool_result, tool_call_id=tool_call["id"]))
        return ToolCallRunResult(tool_messages=tool_messages, trace_entries=trace_entries)

    def _trace_entry(
        self,
        step_index: int,
        tool_call_id: str,
        tool_name: str,
        args: dict[str, Any],
        success: bool,
        result: Any,
    ) -> AgentToolTraceEntry:
        return AgentToolTraceEntry(
            step_index=step_index,
            tool_call_id=tool_call_id,
            tool_name=tool_name,
            args={key: value for key, value in args.items() if str(key) != "sessionSecret"},
            success=success,
            result_preview=self._preview(result),
        )

    def _preview(self, value: Any, limit: int = 500) -> str:
        return str(value or "").replace("\n", "\\n")[:limit]
