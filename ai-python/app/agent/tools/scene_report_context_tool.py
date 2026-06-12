from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class SceneReportContextTool:
    def __init__(self, data_gateway_client: AgentDataGatewayClient | None = None) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str | None = None,
        target_code: str | None = None,
        report_id: str | None = None,
        limit: int = 1,
    ) -> str:
        normalized_limit = max(1, min(int(limit or 1), 3))
        logger.debug(
            "agent tool scene_report_context invoke session_id=%s",
            agent_session_id,
        )
        params: dict[str, Any] = {
            "targetType": target_type,
            "targetCode": target_code,
            "reportId": report_id,
            "limit": normalized_limit,
        }
        self.last_result = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="scene_report.context",
            params=params,
            limit=normalized_limit,
        )
        return json.dumps(self.last_result, ensure_ascii=False, default=str)
