from __future__ import annotations

import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class ConversationHistoryTool:
    def __init__(self, data_gateway_client: AgentDataGatewayClient | None = None) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()

    def query(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        limit: int = 10,
    ) -> list[dict[str, Any]]:
        result = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="conversation.history",
            params={},
            limit=limit,
        )
        rows = result.get("data") if isinstance(result, dict) else []
        logger.info(
            "agent conversation history loaded session_id=%s rows=%s",
            agent_session_id,
            len(rows) if isinstance(rows, list) else 0,
        )
        return rows if isinstance(rows, list) else []
