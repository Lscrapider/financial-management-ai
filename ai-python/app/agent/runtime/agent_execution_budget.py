from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any

UNORDERED_LIST_ARG_NAMES = {"target_codes", "scenes", "sections"}
DEFAULT_MAX_STEPS = 6
DEFAULT_MAX_TOOL_CALLS_TOTAL = 10
DEFAULT_MAX_TOOL_CALLS_PER_STEP = 4
DEFAULT_TIMEOUT_SECONDS = 50.0
DEFAULT_MAX_FINAL_BACKTRACKS = 2


@dataclass
class AgentExecutionBudget:
    max_steps: int = DEFAULT_MAX_STEPS
    max_tool_calls_total: int = DEFAULT_MAX_TOOL_CALLS_TOTAL
    max_tool_calls_per_step: int = DEFAULT_MAX_TOOL_CALLS_PER_STEP
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    max_final_backtracks: int = DEFAULT_MAX_FINAL_BACKTRACKS
    started_at: float = field(default_factory=time.monotonic)
    total_tool_calls: int = 0
    seen_signatures: set[str] = field(default_factory=set)

    @classmethod
    def from_payload(cls, payload: Any) -> "AgentExecutionBudget":
        if not isinstance(payload, dict):
            return cls()
        return cls(
            max_steps=_int_budget(payload, "maxSteps", "max_steps", DEFAULT_MAX_STEPS, 1, 12),
            max_tool_calls_total=_int_budget(
                payload,
                "maxToolCallsTotal",
                "max_tool_calls_total",
                DEFAULT_MAX_TOOL_CALLS_TOTAL,
                1,
                50,
            ),
            max_tool_calls_per_step=_int_budget(
                payload,
                "maxToolCallsPerStep",
                "max_tool_calls_per_step",
                DEFAULT_MAX_TOOL_CALLS_PER_STEP,
                1,
                10,
            ),
            timeout_seconds=float(_int_budget(
                payload,
                "timeoutSeconds",
                "timeout_seconds",
                int(DEFAULT_TIMEOUT_SECONDS),
                10,
                120,
            )),
            max_final_backtracks=_int_budget(
                payload,
                "maxFinalBacktracks",
                "max_final_backtracks",
                DEFAULT_MAX_FINAL_BACKTRACKS,
                0,
                5,
            ),
        )

    def capped_by(self, ceiling: "AgentExecutionBudget") -> "AgentExecutionBudget":
        return AgentExecutionBudget(
            max_steps=min(self.max_steps, ceiling.max_steps),
            max_tool_calls_total=min(self.max_tool_calls_total, ceiling.max_tool_calls_total),
            max_tool_calls_per_step=min(self.max_tool_calls_per_step, ceiling.max_tool_calls_per_step),
            timeout_seconds=min(self.timeout_seconds, ceiling.timeout_seconds),
            max_final_backtracks=min(self.max_final_backtracks, ceiling.max_final_backtracks),
            started_at=self.started_at,
            total_tool_calls=self.total_tool_calls,
            seen_signatures=set(self.seen_signatures),
        )

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
                "args": self._normalized_args(tool_call.get("args") or {}),
            },
            ensure_ascii=False,
            sort_keys=True,
            default=str,
        )

    def _normalized_args(self, args: Any) -> Any:
        if not isinstance(args, dict):
            return args
        return {
            str(key): self._normalized_arg_value(str(key), value)
            for key, value in args.items()
            if value is not None
        }

    def _normalized_arg_value(self, key: str, value: Any) -> Any:
        if key == "target_code" and isinstance(value, str):
            return value.strip().upper()
        if key in UNORDERED_LIST_ARG_NAMES and isinstance(value, list):
            return sorted({str(item).strip().upper() for item in value if str(item).strip()})
        if isinstance(value, dict):
            return self._normalized_args(value)
        return value


def _int_budget(
    payload: dict[str, Any],
    camel_key: str,
    snake_key: str,
    default: int,
    minimum: int,
    maximum: int,
) -> int:
    value = payload.get(camel_key, payload.get(snake_key))
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = default
    return max(minimum, min(parsed, maximum))
