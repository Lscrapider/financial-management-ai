from __future__ import annotations

from typing import Any

from app.core.config import settings


class DeepSeekChatModelFactory:
    def create(self) -> Any:
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            api_key=settings.deepseek.api_key,
            base_url=settings.deepseek.base_url,
            model=settings.deepseek.model,
            temperature=settings.deepseek.temperature,
            timeout=settings.deepseek.timeout_seconds,
            extra_body={
                "thinking": {
                    "type": settings.deepseek.thinking_type,
                },
            },
        )
