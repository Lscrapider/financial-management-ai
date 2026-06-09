from __future__ import annotations

from typing import Any

from app.agent.tools.conversation_history_tool import ConversationHistoryTool


class AgentMemoryLoader:
    def __init__(self, conversation_history_tool: ConversationHistoryTool | None = None) -> None:
        self._conversation_history_tool = conversation_history_tool or ConversationHistoryTool()

    def load_short_term_memory(
        self,
        data_gateway_url: str,
        agent_session_id: str,
        session_secret: str,
        limit: int = 20,
    ) -> list[dict[str, Any]]:
        return self._conversation_history_tool.query(
            data_gateway_url=data_gateway_url,
            agent_session_id=agent_session_id,
            session_secret=session_secret,
            limit=limit,
        )
