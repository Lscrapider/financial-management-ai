from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.services.data_gateway_client import AgentDataGatewayClient
from app.scene_analysis.services.trend_kline_analysis import TrendKlineAnalyzer

logger = logging.getLogger(__name__)


class MarketKlineTrendTool:
    RAW_KLINE_KEYS = {"dailyKlines", "weeklyKlines", "monthlyKlines"}

    def __init__(
        self,
        data_gateway_client: AgentDataGatewayClient | None = None,
        analyzer: TrendKlineAnalyzer | None = None,
    ) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self._analyzer = analyzer or TrendKlineAnalyzer()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str = "stock",
        target_code: str | None = None,
        target_name: str | None = None,
        daily_limit: int = 120,
        weekly_limit: int = 80,
        monthly_limit: int = 60,
    ) -> str:
        logger.info(
            "agent tool market_kline_trend invoke session_id=%s target_type=%s target_code=%s target_name=%s",
            agent_session_id,
            target_type,
            target_code,
            target_name,
        )
        gateway_result = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="market.kline",
            params=self._params(target_type, target_code, target_name, daily_limit, weekly_limit, monthly_limit),
            limit=1,
        )
        self.last_result = self._summarize_result(gateway_result)
        return json.dumps(self.last_result, ensure_ascii=False, default=str)

    def _params(
        self,
        target_type: str,
        target_code: str | None,
        target_name: str | None,
        daily_limit: int,
        weekly_limit: int,
        monthly_limit: int,
    ) -> dict[str, Any]:
        params: dict[str, Any] = {
            "targetType": self._normalize_target_type(target_type),
            "dailyLimit": max(1, min(int(daily_limit or 120), 120)),
            "weeklyLimit": max(1, min(int(weekly_limit or 80), 80)),
            "monthlyLimit": max(1, min(int(monthly_limit or 60), 60)),
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
        return self._compact(
            {
                "targetType": row.get("targetType") or row.get("type"),
                "targetCode": row.get("targetCode") or row.get("code"),
                "targetName": row.get("targetName") or row.get("name"),
                "periods": {
                    "daily": self._compact_trend(self._analyzer.analyze("daily", self._list(row.get("dailyKlines")))),
                    "weekly": self._compact_trend(self._analyzer.analyze("weekly", self._list(row.get("weeklyKlines")))),
                    "monthly": self._compact_trend(self._analyzer.analyze("monthly", self._list(row.get("monthlyKlines")))),
                },
            }
        )

    def _compact_trend(self, trend: dict[str, Any]) -> dict[str, Any]:
        context = self._compact_context(trend.get("context") if isinstance(trend, dict) else {})
        return self._compact(
            {
                "score": trend.get("score"),
                "level": trend.get("level"),
                "direction": trend.get("direction"),
                "tags": trend.get("tags") or {},
                "evidence": trend.get("evidence") or [],
                "context": context,
            }
        )

    def _compact_context(self, context_value: Any) -> dict[str, Any]:
        context = context_value if isinstance(context_value, dict) else {}
        keep_keys = {
            "period",
            "availableBars",
            "startDate",
            "endDate",
            "latestClose",
            "windowBars",
            "windowHigh",
            "windowLow",
            "previousHigh",
            "previousLow",
            "rangePct",
            "position",
            "returnPct",
            "return5Bars",
            "return20Bars",
            "maxDrawdownPct",
            "volatilityPct",
            "volumeRatio5Bars",
            "movingAverages",
            "latestCandle",
            "pathFeatures",
        }
        compact = {key: context.get(key) for key in keep_keys if key in context}
        path_features = compact.get("pathFeatures")
        if isinstance(path_features, dict):
            compact["pathFeatures"] = {
                "method": path_features.get("method"),
                "reversalThresholdPct": path_features.get("reversalThresholdPct"),
                "segments": self._trim_segments(path_features.get("segments")),
            }
        return self._compact(compact)

    def _trim_segments(self, value: Any) -> list[dict[str, Any]]:
        if not isinstance(value, list):
            return []
        segments = [segment for segment in value if isinstance(segment, dict)]
        if len(segments) <= 4:
            return segments
        return [segments[0], *segments[-3:]]

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
        return {
            key: value
            for key, value in data.items()
            if value is not None and value != {} and key not in self.RAW_KLINE_KEYS
        }
