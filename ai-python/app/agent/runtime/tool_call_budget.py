from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ToolCallBudget:
    max_steps: int = 3
    max_tool_calls_total: int = 5
    max_tool_calls_per_step: int = 2
    timeout_seconds: float = 25.0
    started_at: float = field(default_factory=time.monotonic)
    total_tool_calls: int = 0
    seen_signatures: set[str] = field(default_factory=set)

    def step_allowed(self, step_index: int) -> bool:
        return step_index < self.max_steps and not self.timed_out()

    def trim_tool_calls(self, tool_calls: list[dict[str, Any]]) -> list[dict[str, Any]]:
        allowed: list[dict[str, Any]] = []
        for tool_call in tool_calls[: self.max_tool_calls_per_step]:
            if self.total_tool_calls >= self.max_tool_calls_total or self.timed_out():
                break
            signature = self.signature(tool_call)
            if signature in self.seen_signatures:
                continue
            self.seen_signatures.add(signature)
            self.total_tool_calls += 1
            allowed.append(tool_call)
        return allowed

    def timed_out(self) -> bool:
        return time.monotonic() - self.started_at >= self.timeout_seconds

    def signature(self, tool_call: dict[str, Any]) -> str:
        return json.dumps(
            {
                "name": tool_call.get("name"),
                "args": tool_call.get("args") or {},
            },
            ensure_ascii=False,
            sort_keys=True,
            default=str,
        )
