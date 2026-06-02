from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number, percentile_rank


class ValuationProcessor:
    MODULE = "valuation"

    def process(self, context: SceneAnalysisContext, trend_tags: dict[str, float] | None = None) -> SceneModuleResult:
        pe = number(context.base_metrics.get("pe_ttm"))
        pb = number(context.base_metrics.get("pb_ratio"))
        pe_rank = percentile_rank(pe, self._history(context, "peTtm"))
        pb_rank = percentile_rank(pb, self._history(context, "pbMrq"))
        dividend_yield = self._dividend_yield(context)
        dividend_rank = percentile_rank(dividend_yield, self._dividend_history(context))
        low_pe = None if pe_rank is None else clamp(1 - pe_rank)
        low_pb = None if pb_rank is None else clamp(1 - pb_rank)
        high_dividend = dividend_rank
        low_valuation = max([value for value in [low_pe, low_pb, high_dividend] if value is not None], default=None)
        tags = {
            "low_pe": low_pe,
            "high_pe": pe_rank,
            "low_pb": low_pb,
            "high_pb": pb_rank,
            "high_dividend": high_dividend,
            "valuation_repair": self._valuation_repair(context, low_valuation, trend_tags or {}),
        }
        tags = active_tags(tags)
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._direction(tags),
            tags=tags,
            evidence=self._evidence(tags),
        )

    def _history(self, context: SceneAnalysisContext, key: str) -> list[float]:
        values = []
        for row in context.message.get("valuationHistory") or []:
            if isinstance(row, dict):
                value = number(row.get(key))
                if value is not None:
                    values.append(value)
        return values

    def _dividend_yield(self, context: SceneAnalysisContext) -> float | None:
        current_price = number(context.base_metrics.get("current_price") or context.base_metrics.get("latest_price"))
        dividends = [row for row in context.message.get("dividendHistory") or [] if isinstance(row, dict)]
        if not dividends or not current_price:
            return None
        latest_bonus = number(dividends[0].get("pretaxBonusRmb"))
        if latest_bonus is None:
            return None
        return latest_bonus / 10 / current_price * 100

    def _dividend_history(self, context: SceneAnalysisContext) -> list[float]:
        current_price = number(context.base_metrics.get("current_price") or context.base_metrics.get("latest_price"))
        if not current_price:
            return []
        values = []
        for row in context.message.get("dividendHistory") or []:
            if isinstance(row, dict):
                bonus = number(row.get("pretaxBonusRmb"))
                if bonus is not None:
                    values.append(bonus / 10 / current_price * 100)
        return values

    def _valuation_repair(
        self,
        context: SceneAnalysisContext,
        low_valuation: float | None,
        trend_tags: dict[str, float],
    ) -> float | None:
        price_rise = number(context.base_metrics.get("price_rise"))
        uptrend = number(trend_tags.get("uptrend"))
        if None in (low_valuation, price_rise, uptrend):
            return None
        return clamp(low_valuation * price_rise * uptrend)

    def _direction(self, tags: dict[str, float]) -> str:
        if max(tags.get("low_pe", 0.0), tags.get("low_pb", 0.0), tags.get("high_dividend", 0.0), tags.get("valuation_repair", 0.0)) >= 0.3:
            return "positive"
        if max(tags.get("high_pe", 0.0), tags.get("high_pb", 0.0)) >= 0.7:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        messages = {
            "low_pe": "PE 处于历史较低位置",
            "high_pe": "PE 处于历史较高位置",
            "low_pb": "PB 处于历史较低位置",
            "high_pb": "PB 处于历史较高位置",
            "high_dividend": "股息率处于历史较高位置",
            "valuation_repair": "低估值叠加价格上涨和趋势改善",
        }
        return [message for key, message in messages.items() if tags.get(key, 0.0) >= 0.3]
