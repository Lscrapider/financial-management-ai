from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.analysis.intraday_summary_analyzer import IntradaySummaryAnalyzer
from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class MarketIntradaySummaryTool:
    def __init__(
        self,
        data_gateway_client: AgentDataGatewayClient | None = None,
        analyzer: IntradaySummaryAnalyzer | None = None,
    ) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self._analyzer = analyzer or IntradaySummaryAnalyzer()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str = "stock",
        target_code: str | None = None,
        target_name: str | None = None,
    ) -> str:
        logger.info(
            "agent tool market_intraday_summary invoke session_id=%s target_type=%s target_code=%s target_name=%s",
            agent_session_id,
            target_type,
            target_code,
            target_name,
        )
        gateway_result = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="market.intraday",
            params=self._params(target_type, target_code, target_name),
            limit=1,
        )
        self.last_result = self._summarize_result(gateway_result)
        return json.dumps(self.last_result, ensure_ascii=False, default=str)

    def _params(self, target_type: str, target_code: str | None, target_name: str | None) -> dict[str, Any]:
        params: dict[str, Any] = {
            "targetType": self._normalize_target_type(target_type),
        }
        if target_code:
            params["targetCode"] = str(target_code).strip()
        if target_name:
            params["targetName"] = str(target_name).strip()
        return params

    def _summarize_result(self, gateway_result: dict[str, Any]) -> dict[str, Any]:
        result = {key: value for key, value in gateway_result.items() if key != "data"}
        data = gateway_result.get("data") if isinstance(gateway_result, dict) else None
        if not isinstance(data, list):
            return result
        result["data"] = [self._summarize_row(row) for row in data if isinstance(row, dict)]
        return result

    def _summarize_row(self, row: dict[str, Any]) -> dict[str, Any]:
        rows = self._list(row.get("intradayData"))
        return self._compact(
            {
                "targetType": row.get("targetType") or row.get("type"),
                "targetCode": row.get("targetCode") or row.get("code"),
                "targetName": row.get("targetName") or row.get("name"),
                **self._analyzer.summarize(rows),
            }
        )

    def _list(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        return [row for row in value if isinstance(row, dict)]

    def _normalize_target_type(self, target_type: str) -> str:
        normalized = str(target_type or "stock").strip().lower()
        if normalized in {"index", "bond"}:
            return normalized
        return "stock"

    def _compact(self, data: dict[str, Any]) -> dict[str, Any]:
        return {key: value for key, value in data.items() if value is not None and value != {} and key != "intradayData"}
