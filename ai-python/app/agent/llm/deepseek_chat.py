from __future__ import annotations

import re
from typing import Any

from app.core.config import settings


USER_ID_ALLOWED_PATTERN = re.compile(r"[^a-zA-Z0-9_-]")
USER_ID_MAX_LENGTH = 512


class DeepSeekChatModelFactory:
    def create(self, user_id: str | None = None) -> Any:
        from langchain_openai import ChatOpenAI

        extra_body: dict[str, Any] = {
            "thinking": {
                "type": settings.deepseek.thinking_type,
            },
        }
        normalized_user_id = self._normalized_user_id(user_id)
        if normalized_user_id:
            extra_body["user_id"] = normalized_user_id
        return ChatOpenAI(
            api_key=settings.deepseek.api_key,
            base_url=settings.deepseek.base_url,
            model=settings.deepseek.model,
            temperature=settings.deepseek.temperature,
            timeout=settings.deepseek.timeout_seconds,
            extra_body=extra_body,
        )

    def _normalized_user_id(self, user_id: str | None) -> str | None:
        normalized = USER_ID_ALLOWED_PATTERN.sub("_", str(user_id or "").strip())
        normalized = normalized[:USER_ID_MAX_LENGTH]
        return normalized or None
