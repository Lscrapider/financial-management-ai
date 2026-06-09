from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any


class AgentTokenUsageCollector:
    def __init__(self) -> None:
        self._events: list[dict[str, Any]] = []

    def add_message(self, message: Any, phase: str) -> None:
        usage_metadata = self._dict_attr(message, "usage_metadata")
        response_metadata = self._dict_attr(message, "response_metadata")
        native_usage = self._native_usage(response_metadata)

        prompt_tokens = self._first_int(
            usage_metadata.get("input_tokens"),
            native_usage.get("prompt_tokens"),
        )
        completion_tokens = self._first_int(
            usage_metadata.get("output_tokens"),
            native_usage.get("completion_tokens"),
        )
        total_tokens = self._first_int(
            usage_metadata.get("total_tokens"),
            native_usage.get("total_tokens"),
        )
        if total_tokens <= 0:
            total_tokens = prompt_tokens + completion_tokens
        if total_tokens <= 0:
            return

        event = {
            "phase": phase,
            "responseId": self._response_id(message, response_metadata),
            "objectType": "chat.completion",
            "model": response_metadata.get("model_name") or response_metadata.get("model"),
            "finishReason": response_metadata.get("finish_reason"),
            "promptTokens": prompt_tokens,
            "completionTokens": completion_tokens,
            "totalTokens": total_tokens,
            "cachedTokens": self._cached_tokens(usage_metadata, native_usage),
            "reasoningTokens": self._reasoning_tokens(usage_metadata, native_usage),
            "rawResponse": self._safe_json({
                "responseMetadata": response_metadata,
                "usageMetadata": usage_metadata,
            }),
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        self._events.append(event)

    def events(self) -> list[dict[str, Any]]:
        return list(self._events)

    def _dict_attr(self, message: Any, name: str) -> dict[str, Any]:
        value = getattr(message, name, None) or {}
        return value if isinstance(value, dict) else {}

    def _native_usage(self, response_metadata: dict[str, Any]) -> dict[str, Any]:
        value = response_metadata.get("token_usage") or response_metadata.get("usage") or {}
        return value if isinstance(value, dict) else {}

    def _cached_tokens(self, usage_metadata: dict[str, Any], native_usage: dict[str, Any]) -> int:
        input_details = usage_metadata.get("input_token_details") or {}
        prompt_details = native_usage.get("prompt_tokens_details") or {}
        return self._first_int(
            input_details.get("cache_read"),
            input_details.get("cached_tokens"),
            prompt_details.get("cached_tokens"),
            native_usage.get("prompt_cache_hit_tokens"),
        )

    def _reasoning_tokens(self, usage_metadata: dict[str, Any], native_usage: dict[str, Any]) -> int:
        output_details = usage_metadata.get("output_token_details") or {}
        completion_details = native_usage.get("completion_tokens_details") or {}
        return self._first_int(
            output_details.get("reasoning"),
            output_details.get("reasoning_tokens"),
            completion_details.get("reasoning_tokens"),
        )

    def _response_id(self, message: Any, response_metadata: dict[str, Any]) -> str | None:
        value = response_metadata.get("id") or getattr(message, "id", None)
        return str(value) if value else None

    def _first_int(self, *values: Any) -> int:
        for value in values:
            try:
                if value is not None:
                    return int(value)
            except (TypeError, ValueError):
                continue
        return 0

    def _safe_json(self, value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, default=str, separators=(",", ":"))
