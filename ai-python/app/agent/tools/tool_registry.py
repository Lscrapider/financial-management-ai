from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.tools.convertible_bond_context_tool import ConvertibleBondContextTool
from app.agent.tools.knowledge_search_tool import KnowledgeSearchTool
from app.agent.tools.market_intraday_summary_tool import MarketIntradaySummaryTool
from app.agent.tools.market_kline_trend_tool import MarketKlineTrendTool
from app.agent.tools.market_quote_tool import MarketQuoteTool
from app.agent.tools.scene_report_context_tool import SceneReportContextTool
from app.agent.tools.scene_signal_context_tool import SceneSignalContextTool
from app.agent.tools.stock_fundamental_context_tool import StockFundamentalContextTool
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
        stock_fundamental_context_tool: StockFundamentalContextTool | None = None,
        convertible_bond_context_tool: ConvertibleBondContextTool | None = None,
        scene_report_context_tool: SceneReportContextTool | None = None,
        scene_signal_context_tool: SceneSignalContextTool | None = None,
        knowledge_search_tool: KnowledgeSearchTool | None = None,
    ) -> None:
        self._market_quote_tool = market_quote_tool or MarketQuoteTool()
        self._watch_pool_context_tool = watch_pool_context_tool or WatchPoolContextTool()
        self._market_kline_trend_tool = market_kline_trend_tool or MarketKlineTrendTool()
        self._market_intraday_summary_tool = market_intraday_summary_tool or MarketIntradaySummaryTool()
        self._stock_fundamental_context_tool = stock_fundamental_context_tool or StockFundamentalContextTool()
        self._convertible_bond_context_tool = convertible_bond_context_tool or ConvertibleBondContextTool()
        self._scene_report_context_tool = scene_report_context_tool or SceneReportContextTool()
        self._scene_signal_context_tool = scene_signal_context_tool or SceneSignalContextTool()
        self._knowledge_search_tool = knowledge_search_tool or KnowledgeSearchTool()

    def build_langchain_tools(self, context: AgentToolContext, tool_decorator: Any) -> dict[str, Any]:
        @tool_decorator
        def market_quote(
            target_type: str = "stock",
            target_code: str | None = None,
            target_name: str | None = None,
            limit: int = 5,
        ) -> str:
            """查询股票、指数或债券的最新行情快照，适合回答当前价格、涨跌幅、成交、换手和最新报价事实；不要用它分析K线趋势、分时路径、观察池、财务基本面、可转债条款或历史报告。target_type 只能是 stock、index、bond。"""
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

        @tool_decorator
        def stock_fundamental_context(
            target_code: str | None = None,
            target_name: str | None = None,
            sections: list[str] | None = None,
            limit: int = 4,
        ) -> str:
            """查询股票财务、分红、PE/PB历史估值位置等基本面上下文；当前价格、涨跌幅、成交、换手和最新报价事实仍必须使用 market_quote。sections 默认 valuation、financial_indicator、dividend。"""
            return self._stock_fundamental_context_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_code=target_code,
                target_name=target_name,
                sections=sections,
                limit=limit,
            )

        @tool_decorator
        def convertible_bond_context(
            target_code: str | None = None,
            target_name: str | None = None,
            sections: list[str] | None = None,
            limit: int = 8,
        ) -> str:
            """查询可转债条款、溢价率历史、纯债价值、YTM和余额变化上下文；当前价格、涨跌幅、成交、换手和最新报价事实仍必须使用 market_quote。sections 默认 basic、valuation_history、share_change。"""
            return self._convertible_bond_context_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_code=target_code,
                target_name=target_name,
                sections=sections,
                limit=limit,
            )

        @tool_decorator
        def scene_report_context(
            target_type: str | None = None,
            target_code: str | None = None,
            report_id: str | None = None,
            limit: int = 1,
        ) -> str:
            """查询已有报告文本和历史报告结论；有 report_id 时返回报告正文，有 target_code 时返回该标的报告，二者都为空时返回最近报告摘要列表；当前价格、涨跌幅、成交、换手和最新报价事实仍必须使用 market_quote。"""
            return self._scene_report_context_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                report_id=report_id,
                limit=limit,
            )

        @tool_decorator
        def scene_signal_context(
            target_code: str,
            target_name: str,
            target_type: str = "stock",
            scenes: list[str] | None = None,
            total_chunks: int = 6,
        ) -> str:
            """按指定场景计算当前标的的系统场景信号，适合具体标的的估值、趋势、情绪、风险策略、仓位和止损问题；必须同时传入标准 target_code 和 target_name，只知道名称或代码时先调用 market_quote 获取标准代码和名称；scenes 只能从 asset、price、volume、trend、valuation、sentiment、risk_strategy 中选择。返回处理器原始 payload 形态。"""
            return self._scene_signal_context_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                target_type=target_type,
                target_code=target_code,
                target_name=target_name,
                scenes=scenes,
                total_chunks=total_chunks,
            )

        @tool_decorator
        def knowledge_search(
            query_text: str,
            scenes: list[str] | None = None,
            tags: dict[str, Any] | None = None,
            limit: int = 5,
        ) -> str:
            """基于投资知识库检索简洁知识片段，适合在 scene_signal_context 得到场景标签后补充方法、风险和策略依据；返回内容只包含文件名和 chunk 文本。"""
            return self._knowledge_search_tool.invoke(
                data_gateway_url=context.data_gateway_url,
                agent_session_id=context.agent_session_id,
                session_secret=context.session_secret,
                query_text=query_text,
                scenes=scenes,
                tags=tags,
                limit=limit,
            )

        return {
            "market_quote": market_quote,
            "watch_pool_context": watch_pool_context,
            "market_kline_trend": market_kline_trend,
            "market_intraday_summary": market_intraday_summary,
            "stock_fundamental_context": stock_fundamental_context,
            "convertible_bond_context": convertible_bond_context,
            "scene_report_context": scene_report_context,
            "scene_signal_context": scene_signal_context,
            "knowledge_search": knowledge_search,
        }

    @property
    def last_market_quote_result(self) -> dict[str, Any]:
        return self._market_quote_tool.last_result or {}
