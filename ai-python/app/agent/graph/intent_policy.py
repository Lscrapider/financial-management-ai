from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from app.agent.runtime.agent_execution_budget import AgentExecutionBudget

QueryDepth = Literal["brief", "focused", "default"]

ALL_AGENT_TOOL_NAMES = (
    "market_quote",
    "watch_pool_context",
    "market_kline_trend",
    "market_intraday_summary",
    "stock_fundamental_context",
    "convertible_bond_context",
    "scene_report_context",
    "scene_signal_context",
    "knowledge_search",
)

TRADE_ADVICE_KEYWORDS = (
    "买卖建议",
    "操作建议",
    "能买吗",
    "要买吗",
    "要不要买",
    "要不要卖",
    "能不能买",
    "能不能卖",
    "可以买吗",
    "可以卖吗",
    "加仓",
    "减仓",
    "补仓",
    "建仓",
    "清仓",
    "仓位",
    "止盈",
    "止损",
    "买点",
    "卖点",
    "buy",
    "sell",
    "position",
    "stop loss",
    "take profit",
)

DEEP_RESEARCH_KEYWORDS = (
    "完整分析",
    "详细分析",
    "深度分析",
    "全面分析",
    "系统分析",
    "全方位",
    "深入研究",
    "深度研究",
    "完整研究",
    "详细研究",
    "基本面技术面",
    "技术面基本面",
)

FOCUSED_TOOL_KEYWORDS = (
    (("趋势", "走势", "k线", "日线", "周线", "月线", "均线", "破位", "支撑", "压力", "回撤"), ("market_kline_trend",)),
    (("分时", "盘中", "今天", "日内", "冲高回落", "跳水", "拉升", "成交", "换手", "量比", "最新", "当前价格", "涨跌幅"), ("market_quote", "market_intraday_summary")),
    (("基本面", "财务", "业绩", "营收", "利润", "分红", "估值", "pe", "pb", "贵不贵"), ("stock_fundamental_context",)),
    (("可转债", "转债", "溢价率", "纯债", "ytm", "余额"), ("convertible_bond_context",)),
    (("观察池", "自选", "关注列表", "关注标的"), ("watch_pool_context",)),
    (("报告", "历史报告", "reportid", "report id"), ("scene_report_context",)),
    (("场景信号", "知识库", "rag", "风险策略", "风险", "情绪", "策略依据", "标签"), ("scene_signal_context", "knowledge_search")),
)

BRIEF_INTENT_KEYWORDS = (
    "看看",
    "看下",
    "看一下",
    "看一眼",
    "帮我看",
    "帮我看看",
    "这个票怎么样",
    "这票怎么样",
    "这个股票怎么样",
    "这只股票怎么样",
    "这个怎么样",
    "怎么样",
    "咋样",
    "如何",
)

BRIEF_BUDGET_CEILING = AgentExecutionBudget(
    max_steps=1,
    max_tool_calls_total=2,
    max_tool_calls_per_step=2,
    timeout_seconds=25.0,
    max_final_backtracks=0,
)
FOCUSED_BUDGET_CEILING = AgentExecutionBudget(
    max_steps=2,
    max_tool_calls_total=4,
    max_tool_calls_per_step=2,
    timeout_seconds=40.0,
    max_final_backtracks=1,
)


@dataclass(frozen=True)
class QueryIntentPolicy:
    query_depth: QueryDepth
    allowed_tools: tuple[str, ...]
    budget_ceiling: AgentExecutionBudget | None
    planner_hint: str

    def apply_budget(self, user_budget: AgentExecutionBudget) -> AgentExecutionBudget:
        if self.budget_ceiling is None:
            return user_budget
        return user_budget.capped_by(self.budget_ceiling)


def classify_query_intent(user_text: str) -> QueryIntentPolicy:
    text = str(user_text or "").strip()
    normalized_text = text.lower()
    focused_tools = _focused_tools(normalized_text)
    if focused_tools:
        return QueryIntentPolicy(
            query_depth="focused",
            allowed_tools=focused_tools,
            budget_ceiling=FOCUSED_BUDGET_CEILING,
            planner_hint="当前问题只要求指定维度分析，只围绕用户明确提到的维度选择工具，不要扩展成完整投研或买卖建议。",
        )

    if _contains_any(normalized_text, BRIEF_INTENT_KEYWORDS) and not _full_flow_requested(normalized_text):
        return QueryIntentPolicy(
            query_depth="brief",
            allowed_tools=("market_quote", "market_kline_trend"),
            budget_ceiling=BRIEF_BUDGET_CEILING,
            planner_hint="当前问题只是初步看看标的，只做低成本初看；优先查行情和趋势，不要展开估值、基本面、场景信号、知识库或买卖建议。",
        )

    return _default_policy()


def _default_policy() -> QueryIntentPolicy:
    return QueryIntentPolicy(
        query_depth="default",
        allowed_tools=(),
        budget_ceiling=None,
        planner_hint="",
    )


def _full_flow_requested(text: str) -> bool:
    return _contains_any(text, TRADE_ADVICE_KEYWORDS) or _contains_any(text, DEEP_RESEARCH_KEYWORDS)


def _focused_tools(text: str) -> tuple[str, ...]:
    tools: set[str] = set()
    for keywords, tool_names in FOCUSED_TOOL_KEYWORDS:
        if _contains_any(text, keywords):
            tools.update(tool_names)
    if not tools:
        return ()
    if tools != {"watch_pool_context"}:
        tools.add("market_quote")
    return tuple(tool_name for tool_name in ALL_AGENT_TOOL_NAMES if tool_name in tools)


def _contains_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword.lower() in text for keyword in keywords)
