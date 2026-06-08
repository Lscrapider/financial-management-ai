from __future__ import annotations

import json
import logging
import time
from typing import Any
from urllib import error, request

from app.agent.services.signature import AgentSignatureSigner
from app.core.config import settings

logger = logging.getLogger(__name__)


class AgentDataGatewayClient:
    def __init__(self, signer: AgentSignatureSigner | None = None) -> None:
        self._signer = signer or AgentSignatureSigner()

    def query(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        action: str,
        params: dict[str, Any] | None = None,
        limit: int | None = None,
    ) -> dict[str, Any]:
        body: dict[str, Any] = {
            "action": action,
            "params": params or {},
        }
        if limit is not None:
            body["limit"] = limit
        logger.info(
            "agent data gateway query start session_id=%s url=%s action=%s params=%s limit=%s",
            agent_session_id,
            data_gateway_url,
            action,
            params or {},
            limit,
        )
        return self._post(data_gateway_url, body, agent_session_id, session_secret)

    def _post(
        self,
        url: str,
        body: dict[str, Any],
        agent_session_id: str,
        session_secret: str,
    ) -> dict[str, Any]:
        data = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            **self._signer.signed_headers("POST", url, data, agent_session_id, session_secret),
        }
        http_request = request.Request(url, data=data, method="POST", headers=headers)
        started_at = time.monotonic()
        try:
            with request.urlopen(http_request, timeout=settings.finance_api.timeout_seconds) as response:
                response_body = response.read().decode("utf-8", errors="replace")
                elapsed_ms = int((time.monotonic() - started_at) * 1000)
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"agent data query failed status={response.status} body={response_body}")
                result = json.loads(response_body) if response_body else {}
                data = result.get("data") if isinstance(result, dict) else None
                logger.info(
                    "agent data gateway query done session_id=%s url=%s status=%s elapsed_ms=%s success=%s rows=%s body_preview=%s",
                    agent_session_id,
                    url,
                    response.status,
                    elapsed_ms,
                    result.get("success") if isinstance(result, dict) else None,
                    len(data) if isinstance(data, list) else None,
                    response_body[:500],
                )
                return result
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            logger.warning(
                "agent data gateway query http error session_id=%s url=%s status=%s body=%s",
                agent_session_id,
                url,
                exc.code,
                detail,
            )
            raise RuntimeError(f"agent data query failed status={exc.code} body={detail}") from exc
        except error.URLError as exc:
            logger.warning(
                "agent data gateway query request error session_id=%s url=%s reason=%s",
                agent_session_id,
                url,
                exc.reason,
            )
            raise RuntimeError(f"agent data query request failed reason={exc.reason}") from exc
