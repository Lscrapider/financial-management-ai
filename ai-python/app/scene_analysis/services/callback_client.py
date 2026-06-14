from __future__ import annotations

import json
from urllib import error, request

from app.core.config import FinanceApiSettings

CALLBACK_TOKEN_HEADER = "X-Scene-Analysis-Callback-Token"


class SceneAnalysisCallbackClient:
    def __init__(self, settings: FinanceApiSettings) -> None:
        self._settings = settings

    def mark_success(
        self,
        task_no: str,
        callback_token: str,
        current_scenes_payload: dict,
        callback_path: str | None = None,
    ) -> None:
        self._post_callback(
            task_no,
            callback_token,
            {
                "currentScenesPayload": current_scenes_payload,
            },
            callback_path,
        )

    def submit_retrieval_embeddings(
        self,
        task_no: str,
        callback_token: str,
        retrieval_embeddings: list[dict],
        callback_path: str | None = None,
    ) -> None:
        self._post_callback(
            task_no,
            callback_token,
            {
                "retrievalEmbeddings": retrieval_embeddings,
            },
            callback_path,
        )

    def _post_callback(
        self,
        task_no: str,
        callback_token: str,
        body: dict,
        callback_path: str | None = None,
    ) -> None:
        base_url = self._settings.base_url.rstrip("/")
        url = f"{base_url}{self._callback_path(task_no, callback_path)}"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        http_request = request.Request(
            url,
            data=data,
            method="POST",
            headers={
                "Content-Type": "application/json",
                CALLBACK_TOKEN_HEADER: callback_token,
            },
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

    def _callback_path(self, task_no: str, callback_path: str | None) -> str:
        if callback_path is None or not callback_path.strip():
            return f"/api/ai/scene-analysis/tasks/{task_no}/callback"
        path = callback_path.strip().replace("{taskNo}", task_no)
        return path if path.startswith("/") else f"/{path}"
