from typing import Any

from openai import OpenAI

from app.core.config import DeepSeekSettings


class DeepSeekChatEngine:
    def __init__(self, settings: DeepSeekSettings) -> None:
        self._settings = settings
        self._client = OpenAI(
            api_key=settings.api_key or "missing-deepseek-api-key",
            base_url=settings.base_url,
            timeout=settings.timeout_seconds,
        )

    def chat(self, system_prompt: str, user_message: str) -> dict[str, Any]:
        if not self._settings.api_key:
            raise ValueError("DEEPSEEK_API_KEY is required")
        completion = self._client.chat.completions.create(
            model=self._settings.model,
            temperature=self._settings.temperature,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message},
            ],
        )
        content = completion.choices[0].message.content or ""
        return {
            "content": content,
            "usage": completion.usage.model_dump() if completion.usage else None,
        }
