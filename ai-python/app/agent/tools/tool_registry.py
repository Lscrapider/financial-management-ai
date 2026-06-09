from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.tools.market_quote_tool import MarketQuoteTool


@dataclass(frozen=True)
class AgentToolContext:
    data_gateway_url: str
    agent_session_id: str
    session_secret: str


class AgentToolRegistry:
    def __init__(self, market_quote_tool: MarketQuoteTool | None = None) -> None:
        self._market_quote_tool = market_quote_tool or MarketQuoteTool()

    def build_langchain_tools(self, context: AgentToolContext, tool_decorator: Any) -> dict[str, Any]:
        @tool_decorator
        def market_quote(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
            limit: int = 5,
        ) -> str:
            """查询股票、指数或债券的最新行情。target_type 只能是 stock、index、bond。用户只给名称或有轻微错别字时，先尽量纠正为常见证券名称后放入 target_name。"""
            return self._market_quote_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
                limit=limit,
            )

        return {
            "market_quote": market_quote,
        }

    @property
    def last_market_quote_result(self) -> dict[str, Any]:
        return self._market_quote_tool.last_result or {}
