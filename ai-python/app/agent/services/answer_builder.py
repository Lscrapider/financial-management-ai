from __future__ import annotations

from typing import Any


class AgentAnswerBuilder:
    def answer_or_fallback(self, content: str, quote_result: dict[str, Any]) -> str:
        return content.strip()

    def fallback_answer(self, quote_result: dict[str, Any]) -> str:
        if not quote_result.get("success"):
            error = quote_result.get("error") or {}
            return f"行情工具查询失败：{error.get('message') or '未知错误'}。"
        rows = quote_result.get("data") or []
        if not rows:
            return "当前没有查到可用于分析的行情数据。"

        first = rows[0]
        name = first.get("stockName") or first.get("indexName") or first.get("bondName") or "目标标的"
        code = first.get("stockCode") or first.get("indexCode") or first.get("bondCode") or ""
        latest_price = first.get("latestPrice")
        change_percent = first.get("changePercent")
        turnover_rate = first.get("turnoverRate")
        return (
            f"根据行情工具查询到 {name}{f'（{code}）' if code else ''}。"
            f"最新价：{latest_price if latest_price is not None else '暂无'}，"
            f"涨跌幅：{change_percent if change_percent is not None else '暂无'}，"
            f"换手率：{turnover_rate if turnover_rate is not None else '暂无'}。"
            "当前只接入了最新行情，估值、K 线、财务指标和记忆上下文还未纳入，因此不能据此给出确定性买卖结论。"
        )
