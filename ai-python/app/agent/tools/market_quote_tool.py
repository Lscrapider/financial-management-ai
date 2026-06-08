from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class MarketQuoteTool:
    def __init__(self, data_gateway_client: AgentDataGatewayClient | None = None) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str = "stock",
        target_code: str | None = None,
        target_name: str | None = None,
        limit: int = 5,
    ) -> str:
        logger.info(
            "agent tool market_quote invoke session_id=%s target_type=%s target_code=%s target_name=%s limit=%s",
            agent_session_id,
            target_type,
            target_code,
            target_name,
            limit,
        )
        self.last_result = self.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            target_type=target_type,
            target_code=target_code,
            target_name=target_name,
            limit=limit,
        )
        rows = self.last_result.get("data") if isinstance(self.last_result, dict) else None
        logger.info(
            "agent tool market_quote result session_id=%s success=%s rows=%s",
            agent_session_id,
            self.last_result.get("success") if isinstance(self.last_result, dict) else None,
            len(rows) if isinstance(rows, list) else None,
        )
        return json.dumps(self.last_result, ensure_ascii=False, default=str)

    def query(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str = "stock",
        target_code: str | None = None,
        target_name: str | None = None,
        limit: int = 5,
    ) -> dict[str, Any]:
        normalized_limit = max(1, min(int(limit or 5), 10))
        params: dict[str, Any] = {
            "targetType": self._normalize_target_type(target_type),
            "sortField": "changePercent",
            "sortOrder": "desc",
        }
        if target_code:
            params["targetCode"] = str(target_code).strip()
        if target_name:
            params["targetName"] = str(target_name).strip()
        return self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="market.quote",
            params=params,
            limit=normalized_limit,
        )

    def _normalize_target_type(self, target_type: str) -> str:
        normalized = str(target_type or "stock").strip().lower()
        if normalized in {"index", "bond"}:
            return normalized
        return "stock"
