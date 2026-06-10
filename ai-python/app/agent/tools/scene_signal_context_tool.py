from __future__ import annotations

import json
import logging
from typing import Any

from app.agent.scene_signal.scene_signal_runner import SceneSignalRunner
from app.agent.services.data_gateway_client import AgentDataGatewayClient

logger = logging.getLogger(__name__)


class SceneSignalContextTool:
    def __init__(
        self,
        data_gateway_client: AgentDataGatewayClient | None = None,
        runner: SceneSignalRunner | None = None,
    ) -> None:
        self._data_gateway_client = data_gateway_client or AgentDataGatewayClient()
        self._runner = runner or SceneSignalRunner()
        self.last_result: dict[str, Any] | None = None

    def invoke(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str,
        target_code: str | None = None,
        target_name: str | None = None,
        scenes: list[str] | None = None,
        total_chunks: int = 6,
    ) -> str:
        self.last_result = self.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            target_type=target_type,
            target_code=target_code,
            target_name=target_name,
            scenes=scenes,
            total_chunks=total_chunks,
        )
        return json.dumps(self.last_result, ensure_ascii=False, default=str)

    def query(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        target_type: str,
        target_code: str | None = None,
        target_name: str | None = None,
        scenes: list[str] | None = None,
        total_chunks: int = 6,
    ) -> dict[str, Any]:
        requested_total_chunks = int(total_chunks or 6)
        params: dict[str, Any] = {
            "targetType": self._normalize_target_type(target_type),
            "totalChunks": requested_total_chunks,
        }
        if target_code:
            params["targetCode"] = str(target_code).strip()
        if target_name:
            params["targetName"] = str(target_name).strip()
        logger.info(
            "agent tool scene_signal_context invoke session_id=%s target_type=%s target_code=%s scenes=%s total_chunks=%s",
            agent_session_id,
            params["targetType"],
            params.get("targetCode"),
            scenes,
            requested_total_chunks,
        )
        response = self._data_gateway_client.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            action="scene.signal_data",
            params=params,
            limit=1,
        )
        if not response.get("success", False):
            return {
                "success": False,
                "sceneSignals": {},
                "error": response.get("error") or {"message": "场景信号数据查询失败"},
            }
        rows = response.get("data")
        message = rows[0] if isinstance(rows, list) and rows else {}
        if not isinstance(message, dict):
            message = {}
        return {
            "success": True,
            "sceneSignals": self._runner.run(message, scenes),
        }

    def _normalize_target_type(self, target_type: str) -> str:
        normalized = str(target_type or "stock").strip().lower()
        if normalized in {"index"}:
            return "INDEX"
        if normalized in {"bond", "convertible_bond"}:
            return "CONVERTIBLE_BOND"
        return "STOCK"
