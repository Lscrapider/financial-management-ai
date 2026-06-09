from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


class ToolCallRunner:
    def run_standard_tools(
        self,
        tool_calls: list[dict[str, Any]],
        tools_by_name: dict[str, Any],
        agent_session_id: str,
        tool_message_type: Any,
    ) -> list[Any]:
        tool_messages = []
        for tool_call in tool_calls:
            tool_name = str(tool_call.get("name") or "")
            tool = tools_by_name.get(tool_name)
            if tool is None:
                logger.info(
                    "agent langchain skip unsupported tool session_id=%s tool_name=%s",
                    agent_session_id,
                    tool_name,
                )
                continue
            logger.info(
                "agent langchain standard tool call session_id=%s tool_id=%s tool_name=%s args=%s",
                agent_session_id,
                tool_call.get("id"),
                tool_name,
                tool_call.get("args") or {},
            )
            tool_result = tool.invoke(tool_call.get("args") or {})
            tool_messages.append(tool_message_type(content=tool_result, tool_call_id=tool_call["id"]))
        return tool_messages
