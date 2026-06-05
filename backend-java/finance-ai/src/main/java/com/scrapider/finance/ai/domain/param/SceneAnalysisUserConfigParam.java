package com.scrapider.finance.ai.domain.param;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

public record SceneAnalysisUserConfigParam(
        @JsonProperty("asset_type") String assetType,
        @JsonProperty("asset_config") AssetConfigParam assetConfig,
        @JsonProperty("price_config") PriceConfigParam priceConfig,
        @JsonProperty("volume_config") VolumeConfigParam volumeConfig,
        @JsonProperty("sentiment_config") SentimentConfigParam sentimentConfig,
        @JsonProperty("risk_strategy_config") RiskStrategyConfigParam riskStrategyConfig) {

    public static SceneAnalysisUserConfigParam effective(
            SceneAnalysisUserConfigParam overrides,
            String targetType) {
        SceneAnalysisUserConfigParam defaults = defaultsFor(targetType, overrides == null ? null : overrides.assetType());
        if (overrides == null) {
            return defaults;
        }
        return new SceneAnalysisUserConfigParam(
                StrUtil.blankToDefault(overrides.assetType(), defaults.assetType()),
                merge(defaults.assetConfig(), overrides.assetConfig()),
                merge(defaults.priceConfig(), overrides.priceConfig()),
                merge(defaults.volumeConfig(), overrides.volumeConfig()),
                merge(defaults.sentimentConfig(), overrides.sentimentConfig()),
                merge(defaults.riskStrategyConfig(), overrides.riskStrategyConfig()));
    }

    private static SceneAnalysisUserConfigParam defaultsFor(String targetType, String assetTypeOverride) {
        String assetType = StrUtil.blankToDefault(assetTypeOverride, defaultAssetType(targetType));
        return new SceneAnalysisUserConfigParam(
                assetType,
                new AssetConfigParam(5.0),
                new PriceConfigParam(2.0, 1.2, 2.0, 1.2, 2.0, 1.2, 0.08, 0.03),
                new VolumeConfigParam(1.0, 0.8, 1.8, 0.7),
                new SentimentConfigParam(
                        1.5,
                        0.4,
                        0.5,
                        new MarketProxyEmotionWeightsParam(0.35, 0.25, 0.20, 0.20),
                        new MarketProxyPanicWeightsParam(0.35, 0.25, 0.25, 0.15),
                        new MarketProxyWeakWeightsParam(0.25, 0.20, 0.20, 0.20, 0.15),
                        new MarketProxyHerdingWeightsParam(0.35, 0.30, 0.35)),
                new RiskStrategyConfigParam(
                        new ChaseHighRiskWeightsParam(0.25, 0.25, 0.20, 0.15, 0.15),
                        new FalseBreakoutRiskWeightsParam(0.35, 0.25, 0.20, 0.20),
                        new LiquidityRiskWeightsParam(0.40, 0.30),
                        new DrawdownRiskWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new OverheatedRiskWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new PositionControlWeightsParam(0.50, 0.25, 0.25),
                        new TakeProfitPlanWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new StopLossPlanWeightsParam(0.35, 0.25, 0.20, 0.20),
                        0.08,
                        new UncertaintyWeightsParam(1.00)));
    }

    private static String defaultAssetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return "stock";
        }
        return switch (targetType.trim().toUpperCase(Locale.ROOT)) {
            case "INDEX" -> "index";
            case "CONVERTIBLE_BOND", "BOND" -> "convertible_bond";
            default -> "stock";
        };
    }

    private static AssetConfigParam merge(AssetConfigParam defaults, AssetConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new AssetConfigParam(value(overrides.lowPriceThreshold(), defaults.lowPriceThreshold()));
    }

    private static PriceConfigParam merge(PriceConfigParam defaults, PriceConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new PriceConfigParam(
                value(overrides.priceRiseCenter(), defaults.priceRiseCenter()),
                value(overrides.priceRiseScale(), defaults.priceRiseScale()),
                value(overrides.priceDropCenter(), defaults.priceDropCenter()),
                value(overrides.priceDropScale(), defaults.priceDropScale()),
                value(overrides.priceMoveCenter(), defaults.priceMoveCenter()),
                value(overrides.priceMoveScale(), defaults.priceMoveScale()),
                value(overrides.pullbackThreshold(), defaults.pullbackThreshold()),
                value(overrides.gapThreshold(), defaults.gapThreshold()));
    }

    private static VolumeConfigParam merge(VolumeConfigParam defaults, VolumeConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new VolumeConfigParam(
                value(overrides.volumeExpandCenter(), defaults.volumeExpandCenter()),
                value(overrides.volumeExpandScale(), defaults.volumeExpandScale()),
                value(overrides.volumeSpikeCenter(), defaults.volumeSpikeCenter()),
                value(overrides.volumeSpikeScale(), defaults.volumeSpikeScale()));
    }

    private static SentimentConfigParam merge(SentimentConfigParam defaults, SentimentConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new SentimentConfigParam(
                value(overrides.attentionRiseCenter(), defaults.attentionRiseCenter()),
                value(overrides.attentionRiseScale(), defaults.attentionRiseScale()),
                value(overrides.lowAttentionScale(), defaults.lowAttentionScale()),
                merge(defaults.marketProxyEmotionWeights(), overrides.marketProxyEmotionWeights()),
                merge(defaults.marketProxyPanicWeights(), overrides.marketProxyPanicWeights()),
                merge(defaults.marketProxyWeakWeights(), overrides.marketProxyWeakWeights()),
                merge(defaults.marketProxyHerdingWeights(), overrides.marketProxyHerdingWeights()));
    }

    private static RiskStrategyConfigParam merge(
            RiskStrategyConfigParam defaults,
            RiskStrategyConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new RiskStrategyConfigParam(
                merge(defaults.chaseHighRiskWeights(), overrides.chaseHighRiskWeights()),
                merge(defaults.falseBreakoutRiskWeights(), overrides.falseBreakoutRiskWeights()),
                merge(defaults.liquidityRiskWeights(), overrides.liquidityRiskWeights()),
                merge(defaults.drawdownRiskWeights(), overrides.drawdownRiskWeights()),
                merge(defaults.overheatedRiskWeights(), overrides.overheatedRiskWeights()),
                merge(defaults.positionControlWeights(), overrides.positionControlWeights()),
                merge(defaults.takeProfitPlanWeights(), overrides.takeProfitPlanWeights()),
                merge(defaults.stopLossPlanWeights(), overrides.stopLossPlanWeights()),
                value(overrides.supportDistanceThreshold(), defaults.supportDistanceThreshold()),
                merge(defaults.uncertaintyWeights(), overrides.uncertaintyWeights()));
    }

    private static MarketProxyEmotionWeightsParam merge(
            MarketProxyEmotionWeightsParam defaults,
            MarketProxyEmotionWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new MarketProxyEmotionWeightsParam(
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.highTurnover(), defaults.highTurnover()),
                value(overrides.marketAttentionRise(), defaults.marketAttentionRise()));
    }

    private static MarketProxyPanicWeightsParam merge(
            MarketProxyPanicWeightsParam defaults,
            MarketProxyPanicWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new MarketProxyPanicWeightsParam(
                value(overrides.priceDrop(), defaults.priceDrop()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.breakRecentLow(), defaults.breakRecentLow()),
                value(overrides.closeWeak(), defaults.closeWeak()));
    }

    private static MarketProxyWeakWeightsParam merge(
            MarketProxyWeakWeightsParam defaults,
            MarketProxyWeakWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new MarketProxyWeakWeightsParam(
                value(overrides.priceDrop(), defaults.priceDrop()),
                value(overrides.volumeShrink(), defaults.volumeShrink()),
                value(overrides.lowTurnover(), defaults.lowTurnover()),
                value(overrides.closeWeak(), defaults.closeWeak()),
                value(overrides.lowAttention(), defaults.lowAttention()));
    }

    private static MarketProxyHerdingWeightsParam merge(
            MarketProxyHerdingWeightsParam defaults,
            MarketProxyHerdingWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new MarketProxyHerdingWeightsParam(
                value(overrides.highTurnover(), defaults.highTurnover()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.marketAttentionRise(), defaults.marketAttentionRise()));
    }

    private static ChaseHighRiskWeightsParam merge(
            ChaseHighRiskWeightsParam defaults,
            ChaseHighRiskWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new ChaseHighRiskWeightsParam(
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.nearRecentHigh(), defaults.nearRecentHigh()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.highTurnover(), defaults.highTurnover()),
                value(overrides.shortTermEmotion(), defaults.shortTermEmotion()));
    }

    private static FalseBreakoutRiskWeightsParam merge(
            FalseBreakoutRiskWeightsParam defaults,
            FalseBreakoutRiskWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new FalseBreakoutRiskWeightsParam(
                value(overrides.breakout(), defaults.breakout()),
                value(overrides.closeWeak(), defaults.closeWeak()),
                value(overrides.upperShadow(), defaults.upperShadow()),
                value(overrides.volumeExpand(), defaults.volumeExpand()));
    }

    private static LiquidityRiskWeightsParam merge(LiquidityRiskWeightsParam defaults, LiquidityRiskWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new LiquidityRiskWeightsParam(
                value(overrides.lowTurnover(), defaults.lowTurnover()),
                value(overrides.lowVolume(), defaults.lowVolume()));
    }

    private static DrawdownRiskWeightsParam merge(DrawdownRiskWeightsParam defaults, DrawdownRiskWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new DrawdownRiskWeightsParam(
                value(overrides.nearRecentHigh(), defaults.nearRecentHigh()),
                value(overrides.volatility(), defaults.volatility()),
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.supportDistance(), defaults.supportDistance()));
    }

    private static OverheatedRiskWeightsParam merge(OverheatedRiskWeightsParam defaults, OverheatedRiskWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new OverheatedRiskWeightsParam(
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.highTurnover(), defaults.highTurnover()),
                value(overrides.shortTermEmotion(), defaults.shortTermEmotion()));
    }

    private static PositionControlWeightsParam merge(
            PositionControlWeightsParam defaults,
            PositionControlWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new PositionControlWeightsParam(
                value(overrides.riskControl(), defaults.riskControl()),
                value(overrides.volatility(), defaults.volatility()),
                value(overrides.uncertainty(), defaults.uncertainty()));
    }

    private static TakeProfitPlanWeightsParam merge(
            TakeProfitPlanWeightsParam defaults,
            TakeProfitPlanWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new TakeProfitPlanWeightsParam(
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.nearRecentHigh(), defaults.nearRecentHigh()),
                value(overrides.overheatedRisk(), defaults.overheatedRisk()),
                value(overrides.drawdownRisk(), defaults.drawdownRisk()));
    }

    private static StopLossPlanWeightsParam merge(StopLossPlanWeightsParam defaults, StopLossPlanWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new StopLossPlanWeightsParam(
                value(overrides.breakRecentLow(), defaults.breakRecentLow()),
                value(overrides.downtrend(), defaults.downtrend()),
                value(overrides.panicSelling(), defaults.panicSelling()),
                value(overrides.drawdownRisk(), defaults.drawdownRisk()));
    }

    private static UncertaintyWeightsParam merge(UncertaintyWeightsParam defaults, UncertaintyWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new UncertaintyWeightsParam(
                value(overrides.sentimentConflict(), defaults.sentimentConflict()));
    }

    private static Double value(Double overrideValue, Double defaultValue) {
        return overrideValue == null ? defaultValue : overrideValue;
    }

    public record AssetConfigParam(
            @JsonProperty("low_price_threshold") Double lowPriceThreshold) {
    }

    public record PriceConfigParam(
            @JsonProperty("price_rise_center") Double priceRiseCenter,
            @JsonProperty("price_rise_scale") Double priceRiseScale,
            @JsonProperty("price_drop_center") Double priceDropCenter,
            @JsonProperty("price_drop_scale") Double priceDropScale,
            @JsonProperty("price_move_center") Double priceMoveCenter,
            @JsonProperty("price_move_scale") Double priceMoveScale,
            @JsonProperty("pullback_threshold") Double pullbackThreshold,
            @JsonProperty("gap_threshold") Double gapThreshold) {
    }

    public record VolumeConfigParam(
            @JsonProperty("volume_expand_center") Double volumeExpandCenter,
            @JsonProperty("volume_expand_scale") Double volumeExpandScale,
            @JsonProperty("volume_spike_center") Double volumeSpikeCenter,
            @JsonProperty("volume_spike_scale") Double volumeSpikeScale) {
    }

    public record SentimentConfigParam(
            @JsonProperty("attention_rise_center") Double attentionRiseCenter,
            @JsonProperty("attention_rise_scale") Double attentionRiseScale,
            @JsonProperty("low_attention_scale") Double lowAttentionScale,
            @JsonProperty("market_proxy_emotion_weights") MarketProxyEmotionWeightsParam marketProxyEmotionWeights,
            @JsonProperty("market_proxy_panic_weights") MarketProxyPanicWeightsParam marketProxyPanicWeights,
            @JsonProperty("market_proxy_weak_weights") MarketProxyWeakWeightsParam marketProxyWeakWeights,
            @JsonProperty("market_proxy_herding_weights") MarketProxyHerdingWeightsParam marketProxyHerdingWeights) {
    }

    public record RiskStrategyConfigParam(
            @JsonProperty("chase_high_risk_weights") ChaseHighRiskWeightsParam chaseHighRiskWeights,
            @JsonProperty("false_breakout_risk_weights") FalseBreakoutRiskWeightsParam falseBreakoutRiskWeights,
            @JsonProperty("liquidity_risk_weights") LiquidityRiskWeightsParam liquidityRiskWeights,
            @JsonProperty("drawdown_risk_weights") DrawdownRiskWeightsParam drawdownRiskWeights,
            @JsonProperty("overheated_risk_weights") OverheatedRiskWeightsParam overheatedRiskWeights,
            @JsonProperty("position_control_weights") PositionControlWeightsParam positionControlWeights,
            @JsonProperty("take_profit_plan_weights") TakeProfitPlanWeightsParam takeProfitPlanWeights,
            @JsonProperty("stop_loss_plan_weights") StopLossPlanWeightsParam stopLossPlanWeights,
            @JsonProperty("support_distance_threshold") Double supportDistanceThreshold,
            @JsonProperty("uncertainty_weights") UncertaintyWeightsParam uncertaintyWeights) {
    }

    public record MarketProxyEmotionWeightsParam(
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("high_turnover") Double highTurnover,
            @JsonProperty("market_attention_rise") Double marketAttentionRise) {
    }

    public record MarketProxyPanicWeightsParam(
            @JsonProperty("price_drop") Double priceDrop,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("break_recent_low") Double breakRecentLow,
            @JsonProperty("close_weak") Double closeWeak) {
    }

    public record MarketProxyWeakWeightsParam(
            @JsonProperty("price_drop") Double priceDrop,
            @JsonProperty("volume_shrink") Double volumeShrink,
            @JsonProperty("low_turnover") Double lowTurnover,
            @JsonProperty("close_weak") Double closeWeak,
            @JsonProperty("low_attention") Double lowAttention) {
    }

    public record MarketProxyHerdingWeightsParam(
            @JsonProperty("high_turnover") Double highTurnover,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("market_attention_rise") Double marketAttentionRise) {
    }

    public record ChaseHighRiskWeightsParam(
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("near_recent_high") Double nearRecentHigh,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("high_turnover") Double highTurnover,
            @JsonProperty("short_term_emotion") Double shortTermEmotion) {
    }

    public record FalseBreakoutRiskWeightsParam(
            @JsonProperty("breakout") Double breakout,
            @JsonProperty("close_weak") Double closeWeak,
            @JsonProperty("upper_shadow") Double upperShadow,
            @JsonProperty("volume_expand") Double volumeExpand) {
    }

    public record LiquidityRiskWeightsParam(
            @JsonProperty("low_turnover") Double lowTurnover,
            @JsonProperty("low_volume") Double lowVolume) {
    }

    public record DrawdownRiskWeightsParam(
            @JsonProperty("near_recent_high") Double nearRecentHigh,
            @JsonProperty("volatility") Double volatility,
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("support_distance") Double supportDistance) {
    }

    public record OverheatedRiskWeightsParam(
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("high_turnover") Double highTurnover,
            @JsonProperty("short_term_emotion") Double shortTermEmotion) {
    }

    public record PositionControlWeightsParam(
            @JsonProperty("risk_control") Double riskControl,
            @JsonProperty("volatility") Double volatility,
            @JsonProperty("uncertainty") Double uncertainty) {
    }

    public record TakeProfitPlanWeightsParam(
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("near_recent_high") Double nearRecentHigh,
            @JsonProperty("overheated_risk") Double overheatedRisk,
            @JsonProperty("drawdown_risk") Double drawdownRisk) {
    }

    public record StopLossPlanWeightsParam(
            @JsonProperty("break_recent_low") Double breakRecentLow,
            @JsonProperty("downtrend") Double downtrend,
            @JsonProperty("panic_selling") Double panicSelling,
            @JsonProperty("drawdown_risk") Double drawdownRisk) {
    }

    public record UncertaintyWeightsParam(
            @JsonProperty("sentiment_conflict") Double sentimentConflict) {
    }
}
