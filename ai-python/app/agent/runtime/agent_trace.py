from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class AgentToolTraceEntry:
    step_index: int
    tool_call_id: str
    tool_name: str
    args: dict[str, Any]
    success: bool
    result_preview: str


@dataclass
class AgentTrace:
    tool_calls: list[AgentToolTraceEntry] = field(default_factory=list)

    def add_tool_call(
        self,
        step_index: int,
        tool_call_id: str,
        tool_name: str,
        args: dict[str, Any],
        success: bool,
        result: Any,
    ) -> None:
        self.tool_calls.append(
            AgentToolTraceEntry(
                step_index=step_index,
                tool_call_id=tool_call_id,
                tool_name=tool_name,
                args=self._safe_args(args),
                success=success,
                result_preview=self._preview(result),
            )
        )

    def extend(self, entries: list[AgentToolTraceEntry]) -> None:
        self.tool_calls.extend(entries)

    def failed_count(self) -> int:
        return sum(1 for entry in self.tool_calls if not entry.success)

    def _safe_args(self, args: dict[str, Any]) -> dict[str, Any]:
        return {key: value for key, value in args.items() if str(key) != "sessionSecret"}

    def _preview(self, value: Any, limit: int = 500) -> str:
        if isinstance(value, str):
            text = value
        else:
            text = json.dumps(value, ensure_ascii=False, default=str)
        return text.replace("\n", "\\n")[:limit]
