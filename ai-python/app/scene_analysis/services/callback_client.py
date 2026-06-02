from __future__ import annotations

import json
from urllib import error, request

from app.core.config import FinanceApiSettings


class SceneAnalysisCallbackClient:
    def __init__(self, settings: FinanceApiSettings) -> None:
        self._settings = settings

    def mark_success(self, task_no: str, current_scenes_payload: dict) -> None:
        self._post_callback(
            task_no,
            {
                "currentScenesPayload": current_scenes_payload,
            },
        )

    def _post_callback(self, task_no: str, body: dict) -> None:
        base_url = self._settings.base_url.rstrip("/")
        url = f"{base_url}/api/ai/scene-analysis/tasks/{task_no}/callback"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        http_request = request.Request(
            url,
            data=data,
            method="POST",
            headers={"Content-Type": "application/json"},
        )
        try:
            with request.urlopen(http_request, timeout=self._settings.timeout_seconds) as response:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"scene analysis callback failed status={response.status}")
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"scene analysis callback failed status={exc.code} body={detail}") from exc
        except error.URLError as exc:
            raise RuntimeError(f"scene analysis callback request failed reason={exc.reason}") from exc
