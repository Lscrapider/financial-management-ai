from __future__ import annotations

from app.scene_analysis.context import SceneAnalysisContext
from app.scene_analysis.models import SceneModuleResult
from app.scene_analysis.services.evidence import build_evidence
from app.scene_analysis.services.module_scoring import active_tags, clamp, module_level, module_score, number, percentile_rank
from app.scene_analysis.services.tag_applicability import apply_tag_applicability


class ValuationProcessor:
    MODULE = "valuation"

    def process(self, context: SceneAnalysisContext, trend_tags: dict[str, float] | None = None) -> SceneModuleResult:
        if context.is_asset("convertible_bond"):
            return self._process_convertible_bond(context)
        if not context.is_asset("stock"):
            return SceneModuleResult(
                module=self.MODULE,
                score=0.0,
                level="low",
                direction="neutral",
                tags={},
                evidence=[],
            )
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
        tags = apply_tag_applicability(context, active_tags(tags))
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._direction(tags),
            tags=tags,
            evidence=self._evidence(tags),
        )

    def _process_convertible_bond(self, context: SceneAnalysisContext) -> SceneModuleResult:
        premium_rate = number(context.base_metrics.get("premium_rate"))
        conversion_value = number(context.base_metrics.get("conversion_value"))
        pure_bond_value = number(context.base_metrics.get("pure_bond_value"))
        ytm = number(context.base_metrics.get("ytm"))
        bond_price = number(context.base_metrics.get("bond_price") or context.base_metrics.get("latest_price"))
        premium_history = [
            value for value in (number(item) for item in context.base_metrics.get("premium_rate_history") or [])
            if value is not None
        ]
        conversion_value_history = [
            value for value in (number(item) for item in context.base_metrics.get("conversion_value_history") or [])
            if value is not None
        ]
        premium_low_threshold = number(context.convertible_bond_config.get("premium_low_threshold"))
        premium_high_threshold = number(context.convertible_bond_config.get("premium_high_threshold"))
        high_ytm_threshold = number(context.convertible_bond_config.get("high_ytm_threshold"))
        low_ytm_threshold = number(context.convertible_bond_config.get("low_ytm_threshold"))

        tags = apply_tag_applicability(context, active_tags({
            "convertible_low_premium": self._low_threshold_score(premium_rate, premium_low_threshold),
            "convertible_high_premium": self._high_threshold_score(premium_rate, premium_high_threshold),
            "convertible_premium_compression": self._history_drop_score(premium_rate, premium_history),
            "convertible_premium_expansion": self._history_rise_score(premium_rate, premium_history),
            "convertible_debt_floor_support": self._debt_floor_support(bond_price, pure_bond_value),
            "convertible_high_ytm": self._high_threshold_score(ytm, high_ytm_threshold),
            "convertible_low_ytm": self._low_ytm_score(ytm, low_ytm_threshold),
            "convertible_high_conversion_value": percentile_rank(conversion_value, conversion_value_history),
        }))
        score = module_score(tags)
        return SceneModuleResult(
            module=self.MODULE,
            score=score,
            level=module_level(score),
            direction=self._convertible_direction(tags),
            tags=tags,
            evidence=self._convertible_evidence(tags),
        )

    def _low_threshold_score(self, value: float | None, threshold: float | None) -> float | None:
        if value is None or threshold is None or threshold <= 0:
            return None
        return clamp((threshold - value) / threshold)

    def _high_threshold_score(self, value: float | None, threshold: float | None) -> float | None:
        if value is None or threshold is None or threshold <= 0:
            return None
        return clamp(value / threshold - 1)

    def _low_ytm_score(self, value: float | None, threshold: float | None) -> float | None:
        if value is None or threshold is None:
            return None
        denominator = abs(threshold) if threshold != 0 else 1.0
        return clamp((threshold - value) / denominator)

    def _history_drop_score(self, current: float | None, history: list[float]) -> float | None:
        if current is None or len(history) < 2:
            return None
        previous = history[-2]
        return clamp((previous - current) / max(abs(previous), 1.0))

    def _history_rise_score(self, current: float | None, history: list[float]) -> float | None:
        if current is None or len(history) < 2:
            return None
        previous = history[-2]
        return clamp((current - previous) / max(abs(previous), 1.0))

    def _debt_floor_support(self, bond_price: float | None, pure_bond_value: float | None) -> float | None:
        if bond_price is None or pure_bond_value is None or bond_price <= 0:
            return None
        return clamp(1 - (bond_price - pure_bond_value) / bond_price)

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

    def _convertible_direction(self, tags: dict[str, float]) -> str:
        positive = max(
            tags.get("convertible_low_premium", 0.0),
            tags.get("convertible_debt_floor_support", 0.0),
            tags.get("convertible_high_ytm", 0.0),
            tags.get("convertible_high_conversion_value", 0.0),
        )
        negative = max(tags.get("convertible_high_premium", 0.0), tags.get("convertible_low_ytm", 0.0))
        if positive > negative and positive >= 0.3:
            return "positive"
        if negative > positive and negative >= 0.3:
            return "negative"
        return "neutral"

    def _evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "low_pe": "PE 在历史估值分布中处于较低位置，low_pe 标签触发",
            "high_pe": "PE 在历史估值分布中处于较高位置，high_pe 标签触发",
            "low_pb": "PB 在历史估值分布中处于较低位置，low_pb 标签触发",
            "high_pb": "PB 在历史估值分布中处于较高位置，high_pb 标签触发",
            "high_dividend": "股息率在历史分布中处于较高位置，high_dividend 标签触发",
            "valuation_repair": "低估值信号叠加价格上涨与趋势改善，valuation_repair 标签触发",
        }
        return build_evidence(tags, reasons)

    def _convertible_evidence(self, tags: dict[str, float]) -> list[str]:
        reasons = {
            "convertible_low_premium": "转股溢价率低于低溢价阈值，convertible_low_premium 标签触发",
            "convertible_high_premium": "转股溢价率高于高溢价阈值，convertible_high_premium 标签触发",
            "convertible_premium_compression": "近期转股溢价率下降，convertible_premium_compression 标签触发",
            "convertible_premium_expansion": "近期转股溢价率上升，convertible_premium_expansion 标签触发",
            "convertible_debt_floor_support": "转债价格接近纯债价值，convertible_debt_floor_support 标签触发",
            "convertible_high_ytm": "到期收益率高于配置阈值，convertible_high_ytm 标签触发",
            "convertible_low_ytm": "到期收益率低于配置阈值，convertible_low_ytm 标签触发",
            "convertible_high_conversion_value": "转股价值处于历史分布较高位置，convertible_high_conversion_value 标签触发",
        }
        return build_evidence(tags, reasons)
