from dataclasses import dataclass, field
from typing import Any

SCENE_NAMES = {
    "asset": "资产类型",
    "price": "价格分析",
    "volume": "成交量分析",
    "trend": "趋势分析",
    "valuation": "估值分析",
    "sentiment": "情绪分析",
    "risk_strategy": "风险策略",
}

TAG_NAMES = {
    "general": "通用标的",
    "stock": "股票",
    "index": "指数",
    "convertible_bond": "可转债",
    "fund": "基金",
    "bank_stock": "银行股",
    "low_price_stock": "低价股",
    "large_cap_stock": "大盘股",
    "small_cap_stock": "小盘股",
    "price_rise": "价格上涨",
    "price_drop": "价格下跌",
    "sideways": "横盘震荡",
    "near_recent_high": "接近近期高位",
    "near_recent_low": "接近近期低位",
    "breakout": "价格突破",
    "break_recent_low": "跌破近期低位",
    "pullback": "回调",
    "gap_up": "跳空高开",
    "gap_down": "跳空低开",
    "convertible_high_price_risk": "转债高价风险",
    "convertible_low_price_defensive": "转债低价防御",
    "volume_expand": "放量",
    "volume_shrink": "缩量",
    "high_turnover": "高换手",
    "low_turnover": "低换手",
    "volume_price_confirm": "量价确认",
    "volume_price_divergence": "量价背离",
    "volume_spike": "成交量突增",
    "volume_dry_up": "成交萎缩",
    "uptrend": "上升趋势",
    "downtrend": "下降趋势",
    "range_bound": "区间震荡",
    "rebound": "反弹",
    "repair": "修复",
    "trend_reversal": "趋势反转",
    "breakout_from_range": "区间突破",
    "breakdown_from_range": "区间破位",
    "continuation": "趋势延续",
    "turn_weak": "转弱",
    "turn_strong": "转强",
    "failed_breakout": "突破失败",
    "low_pe": "低PE",
    "high_pe": "高PE",
    "low_pb": "低PB",
    "high_pb": "高PB",
    "high_dividend": "高股息",
    "valuation_repair": "估值修复",
    "valuation_trap": "估值陷阱",
    "fundamental_risk": "基本面风险",
    "convertible_low_premium": "转股低溢价",
    "convertible_high_premium": "转股高溢价",
    "convertible_premium_compression": "溢价压缩",
    "convertible_premium_expansion": "溢价扩张",
    "convertible_debt_floor_support": "债底支撑",
    "convertible_high_ytm": "到期收益率较高",
    "convertible_low_ytm": "到期收益率较低",
    "convertible_high_conversion_value": "转股价值较高",
    "market_attention_rise": "市场关注度提升",
    "short_term_emotion": "短线情绪",
    "panic_selling": "恐慌抛售",
    "news_driven": "消息驱动",
    "policy_driven": "政策驱动",
    "sector_rotation": "板块轮动",
    "weak_sentiment": "情绪偏弱",
    "herding_effect": "羊群效应",
    "institutional_behavior": "机构行为",
    "convertible_stock_linkage": "正股联动",
    "convertible_independent_strength": "转债独立走强",
    "chase_high_risk": "追高风险",
    "false_breakout_risk": "假突破风险",
    "liquidity_risk": "流动性风险",
    "drawdown_risk": "回撤风险",
    "valuation_trap_risk": "估值陷阱风险",
    "overheated_risk": "过热风险",
    "risk_control": "风险控制",
    "position_control": "仓位管理",
    "wait_confirm": "等待确认",
    "observe_next_day": "观察次日确认",
    "avoid_emotional_trade": "避免情绪化交易",
    "take_profit_plan": "止盈计划",
    "stop_loss_plan": "止损计划",
    "convertible_forced_redeem_risk": "强赎风险",
    "convertible_putback_risk": "回售相关风险",
    "convertible_low_rating_risk": "低评级风险",
    "convertible_small_balance_risk": "剩余规模过小风险",
    "convertible_liquidity_risk": "转债流动性风险",
}


@dataclass(frozen=True)
class BaseMetrics:
    values: dict[str, Any] = field(default_factory=dict)
    missing: list[str] = field(default_factory=list)

    def get(self, key: str, default: Any = None) -> Any:
        return self.values.get(key, default)


@dataclass(frozen=True)
class SceneModuleResult:
    module: str
    score: float
    level: str
    direction: str
    tags: dict[str, float] = field(default_factory=dict)
    evidence: list[str] = field(default_factory=list)
    extra: dict[str, Any] = field(default_factory=dict)
    query_text_override: str | None = None

    def to_dict(self) -> dict[str, Any]:
        payload = {
            "module": self.module,
            "score": self.score,
            "level": self.level,
            "direction": self.direction,
            "tags": self.tags,
            "evidence": self.evidence,
            "queryText": self.query_text(),
        }
        payload.update(self.extra)
        return payload

    def query_text(self) -> str:
        if self.query_text_override:
            return self.query_text_override
        scene_name = SCENE_NAMES.get(self.module, self.module)
        evidence_tags = self._evidence_tags()
        tag_names = [
            TAG_NAMES.get(tag, tag)
            for tag in self.tags
            if tag in evidence_tags
        ]
        prefix = scene_name
        if tag_names:
            prefix = f"{prefix}，{'、'.join(tag_names)}"
        evidence_text = "，".join(
            self._clean_evidence(item)
            for item in self.evidence
            if item and item.strip()
        )
        if evidence_text:
            return f"{prefix}。{evidence_text}。"
        return f"{prefix}。"

    def _clean_evidence(self, evidence: str) -> str:
        cleaned = evidence
        for tag in self.tags:
            cleaned = cleaned.replace(f"，{tag} 标签触发", "")
            cleaned = cleaned.replace(f"，{tag} 标签命中", "")
            cleaned = cleaned.replace(f"{tag} 标签触发", "")
            cleaned = cleaned.replace(f"{tag} 标签命中", "")
        return cleaned.strip("，。 ")

    def _evidence_tags(self) -> set[str]:
        return {
            tag
            for tag in self.tags
            if any(f"{tag} 标签触发" in item or f"{tag} 标签命中" in item for item in self.evidence)
        }
