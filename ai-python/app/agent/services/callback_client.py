from __future__ import annotations

import json
from urllib import error, request

from app.agent.services.signature import AgentSignatureSigner
from app.core.config import settings


class AgentCallbackClient:
    def __init__(self, signer: AgentSignatureSigner | None = None) -> None:
        self._signer = signer or AgentSignatureSigner()

    def send_final_answer(
        self,
        callback_url: str,
        agent_session_id: str,
        session_secret: str,
        conversation_id: str,
        message_id: str,
        answer: str,
    ) -> None:
        body = {
            "agentSessionId": agent_session_id,
            "conversationId": conversation_id,
            "messageId": message_id,
            "eventType": "final_answer",
            "payload": {
                "answer": answer,
            },
        }
        self._post(callback_url, body, agent_session_id, session_secret)

    def _post(
        self,
        url: str,
        body: dict,
        agent_session_id: str,
        session_secret: str,
    ) -> None:
        data = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            **self._signer.signed_headers("POST", url, data, agent_session_id, session_secret),
        }
        http_request = request.Request(url, data=data, method="POST", headers=headers)
        try:
            with request.urlopen(http_request, timeout=settings.finance_api.timeout_seconds) as response:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"agent callback failed status={response.status}")
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"agent callback failed status={exc.code} body={detail}") from exc
        except error.URLError as exc:
            raise RuntimeError(f"agent callback request failed reason={exc.reason}") from exc
