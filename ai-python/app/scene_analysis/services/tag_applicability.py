from __future__ import annotations

from collections.abc import Mapping

from app.scene_analysis.context import SceneAnalysisContext


COMMON_ASSET_TYPES = {"stock", "index", "convertible_bond", "fund"}

TAG_ASSET_TYPES: dict[str, set[str]] = {
    "general": COMMON_ASSET_TYPES,
    "stock": {"stock"},
    "index": {"index"},
    "convertible_bond": {"convertible_bond"},
    "fund": {"fund"},
    "etf": {"fund"},
    "lof": {"fund"},
    "index_fund": {"fund"},
    "active_fund": {"fund"},
    "bond_fund": {"fund"},
    "money_fund": {"fund"},
    "qdii_fund": {"fund"},
    "bank_stock": {"stock"},
    "low_price_stock": {"stock"},
    "large_cap_stock": {"stock"},
    "small_cap_stock": {"stock"},
    "gap_up": {"stock", "index", "convertible_bond"},
    "gap_down": {"stock", "index", "convertible_bond"},
    "convertible_high_price_risk": {"convertible_bond"},
    "convertible_low_price_defensive": {"convertible_bond"},
    "high_turnover": {"stock", "convertible_bond", "fund"},
    "low_turnover": {"stock", "convertible_bond", "fund"},
    "fund_share_growth": {"fund"},
    "fund_share_shrink": {"fund"},
    "fund_nav_uptrend": {"fund"},
    "fund_nav_downtrend": {"fund"},
    "low_pe": {"stock"},
    "high_pe": {"stock"},
    "low_pb": {"stock"},
    "high_pb": {"stock"},
    "high_dividend": {"stock"},
    "valuation_repair": {"stock"},
    "valuation_trap": {"stock"},
    "fundamental_risk": {"stock", "convertible_bond", "fund"},
    "convertible_low_premium": {"convertible_bond"},
    "convertible_high_premium": {"convertible_bond"},
    "convertible_premium_compression": {"convertible_bond"},
    "convertible_premium_expansion": {"convertible_bond"},
    "convertible_debt_floor_support": {"convertible_bond"},
    "convertible_high_ytm": {"convertible_bond"},
    "convertible_low_ytm": {"convertible_bond"},
    "convertible_high_conversion_value": {"convertible_bond"},
    "fund_premium": {"fund"},
    "fund_discount": {"fund"},
    "fund_high_fee": {"fund"},
    "fund_large_scale": {"fund"},
    "fund_small_scale": {"fund"},
    "fund_tracking_error": {"fund"},
    "fund_high_drawdown": {"fund"},
    "fund_stable_nav": {"fund"},
    "short_term_emotion": {"stock", "index", "convertible_bond", "fund"},
    "sector_rotation": {"stock", "index", "fund"},
    "herding_effect": {"stock", "index", "convertible_bond", "fund"},
    "institutional_behavior": {"stock", "index", "fund"},
    "convertible_stock_linkage": {"convertible_bond"},
    "convertible_independent_strength": {"convertible_bond"},
    "fund_flow_in": {"fund"},
    "fund_flow_out": {"fund"},
    "observe_next_day": {"stock", "index", "convertible_bond", "fund"},
    "valuation_trap_risk": {"stock"},
    "convertible_forced_redeem_risk": {"convertible_bond"},
    "convertible_putback_risk": {"convertible_bond"},
    "convertible_low_rating_risk": {"convertible_bond"},
    "convertible_small_balance_risk": {"convertible_bond"},
    "convertible_liquidity_risk": {"convertible_bond"},
    "fund_tracking_deviation_risk": {"fund"},
    "fund_concentration_risk": {"fund"},
    "fund_credit_risk": {"fund"},
    "fund_duration_risk": {"fund"},
    "fund_qdii_fx_risk": {"fund"},
    "fund_liquidity_risk": {"fund"},
}


def apply_tag_applicability(context: SceneAnalysisContext, tags: Mapping[str, float]) -> dict[str, float]:
    asset_type = context.asset_type
    if asset_type is None:
        return dict(tags)
    return {
        tag: score
        for tag, score in tags.items()
        if asset_type in TAG_ASSET_TYPES.get(tag, COMMON_ASSET_TYPES)
    }
