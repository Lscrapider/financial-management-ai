from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AgentPlan:
    planning_message: Any
    standard_tool_calls: list[dict[str, Any]]
    content: str


class AgentPlanner:
    def plan(
        self,
        model: Any,
        messages: list[Any],
        tools: list[Any],
        agent_session_id: str,
    ) -> AgentPlan:
        logger.info(
            "agent langchain tool planning start session_id=%s model=%s base_url=%s thinking_type=%s",
            agent_session_id,
            settings.deepseek.model,
            settings.deepseek.base_url,
            settings.deepseek.thinking_type,
        )
        planning_message = model.bind_tools(tools).invoke(messages)
        standard_tool_calls = getattr(planning_message, "tool_calls", []) or []
        content = str(getattr(planning_message, "content", "") or "")
        logger.info(
            "agent langchain tool planning done session_id=%s standard_tool_calls=%s content_preview=%s tool_calls_preview=%s",
            agent_session_id,
            len(standard_tool_calls),
            self._preview(content),
            self._tool_calls_preview(standard_tool_calls),
        )
        return AgentPlan(
            planning_message=planning_message,
            standard_tool_calls=standard_tool_calls,
            content=content.strip(),
        )

    def _preview(self, value: Any, limit: int = 200) -> str:
        text = str(value or "").replace("\n", "\\n")
        return text[:limit]

    def _tool_calls_preview(self, tool_calls: list[dict[str, Any]], limit: int = 500) -> str:
        preview = []
        for tool_call in tool_calls:
            preview.append({
                "name": tool_call.get("name"),
                "args": tool_call.get("args") or {},
            })
        return self._preview(json.dumps(preview, ensure_ascii=False, default=str), limit)
