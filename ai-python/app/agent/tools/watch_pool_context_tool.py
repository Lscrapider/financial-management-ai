from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class WatchPoolContextTool:
    def __init__(self, data_gateway_client: AgentDataGatewayClient | None = None) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        limit: int = 20,
    ) -> str:
        normalized_limit = max(1, min(int(limit or 20), 50))
        logger.info(
            "agent tool watch_pool_context invoke session_id=%s limit=%s",
            agent_session_id,
            normalized_limit,
        )
        self.last_result = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="watch_pool.context",
            params={},
            limit=normalized_limit,
        )
        return json.dumps(self._compact_result(self.last_result, normalized_limit), ensure_ascii=False, default=str)

    def _compact_result(self, result: dict[str, Any], limit: int) -> dict[str, Any]:
        data = result.get("data") if isinstance(result, dict) else None
        if not isinstance(data, list):
            return result
        compact = dict(result)
        compact["data"] = data[:limit]
        return compact
