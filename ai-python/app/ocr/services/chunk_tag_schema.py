SCENE_CATEGORIES = (
    "asset",
    "price",
    "volume",
    "trend",
    "valuation",
    "sentiment",
    "risk_strategy",
)

VALID_TAGS: dict[str, frozenset[str]] = {
    "asset": frozenset(
        {
            "general",
            "stock",
            "index",
            "convertible_bond",
            "fund",
            "bank_stock",
            "low_price_stock",
            "large_cap_stock",
            "small_cap_stock",
        }
    ),
    "price": frozenset(
        {
            "price_rise",
            "price_drop",
            "sideways",
            "near_recent_high",
            "near_recent_low",
            "breakout",
            "pullback",
            "gap_up",
            "gap_down",
        }
    ),
    "volume": frozenset(
        {
            "volume_expand",
            "volume_shrink",
            "high_turnover",
            "low_turnover",
            "volume_price_confirm",
            "volume_price_divergence",
            "volume_spike",
            "volume_dry_up",
        }
    ),
    "trend": frozenset(
        {
            "uptrend",
            "downtrend",
            "range_bound",
            "rebound",
            "pullback",
            "repair",
            "trend_reversal",
            "breakout_from_range",
            "breakdown_from_range",
            "continuation",
            "turn_weak",
            "turn_strong",
            "failed_breakout",
        }
    ),
    "valuation": frozenset(
        {
            "low_pe",
            "high_pe",
            "low_pb",
            "high_pb",
            "high_dividend",
            "valuation_repair",
            "valuation_trap",
            "fundamental_risk",
        }
    ),
    "sentiment": frozenset(
        {
            "market_attention_rise",
            "short_term_emotion",
            "panic_selling",
            "news_driven",
            "policy_driven",
            "sector_rotation",
            "weak_sentiment",
            "herding_effect",
            "institutional_behavior",
        }
    ),
    "risk_strategy": frozenset(
        {
            "chase_high_risk",
            "false_breakout_risk",
            "liquidity_risk",
            "drawdown_risk",
            "valuation_trap_risk",
            "overheated_risk",
            "risk_control",
            "position_control",
            "wait_confirm",
            "observe_next_day",
            "avoid_emotional_trade",
            "take_profit_plan",
            "stop_loss_plan",
        }
    ),
}


def empty_scenes() -> dict[str, list[str]]:
    return {category: [] for category in SCENE_CATEGORIES}
