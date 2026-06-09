from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.tools.market_intraday_summary_tool import MarketIntradaySummaryTool
from app.agent.tools.market_kline_trend_tool import MarketKlineTrendTool
from app.agent.tools.market_quote_tool import MarketQuoteTool
from app.agent.tools.watch_pool_context_tool import WatchPoolContextTool


@dataclass(frozen=True)
class AgentToolContext:
    data_gateway_url: str
    agent_session_id: str
    session_secret: str


class AgentToolRegistry:
    def __init__(
        self,
        market_quote_tool: MarketQuoteTool | None = None,
        watch_pool_context_tool: WatchPoolContextTool | None = None,
        market_kline_trend_tool: MarketKlineTrendTool | None = None,
        market_intraday_summary_tool: MarketIntradaySummaryTool | None = None,
    ) -> None:
        self._market_quote_tool = market_quote_tool or MarketQuoteTool()
        self._watch_pool_context_tool = watch_pool_context_tool or WatchPoolContextTool()
        self._market_kline_trend_tool = market_kline_trend_tool or MarketKlineTrendTool()
        self._market_intraday_summary_tool = market_intraday_summary_tool or MarketIntradaySummaryTool()

    def build_langchain_tools(self, context: AgentToolContext, tool_decorator: Any) -> dict[str, Any]:
        @tool_decorator
        def market_quote(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
            limit: int = 5,
        ) -> str:
            """查询股票、指数或债券的最新行情快照，适合回答当前价格、涨跌幅、成交、换手等实时事实；不要用它分析K线趋势、分时路径或观察池。target_type 只能是 stock、index、bond。"""
            return self._market_quote_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
                limit=limit,
            )

        @tool_decorator
        def watch_pool_context(limit: int = 20) -> str:
            """查询当前用户观察池、自选分组、关注标的和备注上下文，适合回答“我关注的票”“自选池里”等问题；不要用它查询非当前用户数据，也不要代替行情、K线或分时工具。"""
            return self._watch_pool_context_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                limit=limit,
            )

        @tool_decorator
        def market_kline_trend(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
            daily_limit: int = 120,
            weekly_limit: int = 80,
            monthly_limit: int = 60,
        ) -> str:
            """查询并压缩股票、指数或债券的日线、周线、月线趋势摘要，适合回答趋势、破位、均线结构、区间位置、回撤和波动；不要用它回答当前实时价格或盘中分时路径。target_type 只能是 stock、index、bond。"""
            return self._market_kline_trend_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
                daily_limit=daily_limit,
                weekly_limit=weekly_limit,
                monthly_limit=monthly_limit,
            )

        @tool_decorator
        def market_intraday_summary(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
        ) -> str:
            """查询并压缩最近一个交易日分时摘要，适合回答盘中路径、冲高回落、跳水、拉升、日内高低点和成交集中度；不要用它判断日K、周K、月K趋势，也不要把分时价格当作实时快照。target_type 只能是 stock、index、bond。"""
            return self._market_intraday_summary_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
            )

        return {
            "market_quote": market_quote,
            "watch_pool_context": watch_pool_context,
            "market_kline_trend": market_kline_trend,
            "market_intraday_summary": market_intraday_summary,
        }

    @property
    def last_market_quote_result(self) -> dict[str, Any]:
        return self._market_quote_tool.last_result or {}
