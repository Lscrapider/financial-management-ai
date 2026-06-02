package com.scrapider.finance.ai.domain.param;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

public record SceneAnalysisUserConfigParam(
        @JsonProperty("asset_type") String assetType,
        @JsonProperty("asset_config") AssetConfigParam assetConfig,
        @JsonProperty("price_config") PriceConfigParam priceConfig,
        @JsonProperty("volume_config") VolumeConfigParam volumeConfig,
        @JsonProperty("trend_config") TrendConfigParam trendConfig,
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
                merge(defaults.trendConfig(), overrides.trendConfig()),
                merge(defaults.sentimentConfig(), overrides.sentimentConfig()),
                merge(defaults.riskStrategyConfig(), overrides.riskStrategyConfig()));
    }

    private static SceneAnalysisUserConfigParam defaultsFor(String targetType, String assetTypeOverride) {
        String assetType = StrUtil.blankToDefault(assetTypeOverride, defaultAssetType(targetType));
        return new SceneAnalysisUserConfigParam(
                assetType,
                new AssetConfigParam(5.0),
                new PriceConfigParam(2.0, 1.2, 2.0, 1.2, 2.0, 1.2, 0.08, 0.03),
                new VolumeConfigParam(1.0, 0.8, 1.8, 0.7, "asset_history_then_industry", "asset_history_then_industry"),
                new TrendConfigParam(0.05, new BreakoutFromRangeConfirmWeightsParam(0.70, 0.30)),
                new SentimentConfigParam(
                        1.5,
                        0.8,
                        24.0,
                        24.0,
                        48.0,
                        new SourceWeightsParam(1.00, 0.80, 0.70, 0.40, 0.25),
                        new ShortTermEmotionWeightsParam(0.35, 0.25, 0.25, 0.15),
                        new PanicSellingWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new SectorRotationWeightsParam(0.40, 0.30, 0.30),
                        new WeakSentimentWeightsParam(0.35, 0.25, 0.20, 0.20),
                        new HerdingEffectWeightsParam(0.30, 0.25, 0.25, 0.20)),
                new RiskStrategyConfigParam(
                        new ChaseHighRiskWeightsParam(0.25, 0.25, 0.20, 0.15, 0.15),
                        new FalseBreakoutRiskWeightsParam(0.35, 0.25, 0.20, 0.20),
                        new LiquidityRiskWeightsParam(0.40, 0.30, 0.30),
                        new DrawdownRiskWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new OverheatedRiskWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new PositionControlWeightsParam(0.50, 0.25, 0.25),
                        new TakeProfitPlanWeightsParam(0.30, 0.25, 0.25, 0.20),
                        new StopLossPlanWeightsParam(0.35, 0.25, 0.20, 0.20),
                        0.01,
                        0.08,
                        new UncertaintyWeightsParam(0.30, 0.25, 0.25, 0.20)));
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
                value(overrides.volumeSpikeScale(), defaults.volumeSpikeScale()),
                StrUtil.blankToDefault(overrides.volumeDistributionSource(), defaults.volumeDistributionSource()),
                StrUtil.blankToDefault(overrides.turnoverDistributionSource(), defaults.turnoverDistributionSource()));
    }

    private static TrendConfigParam merge(TrendConfigParam defaults, TrendConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new TrendConfigParam(
                value(overrides.reboundThreshold(), defaults.reboundThreshold()),
                merge(defaults.breakoutFromRangeConfirmWeights(), overrides.breakoutFromRangeConfirmWeights()));
    }

    private static SentimentConfigParam merge(SentimentConfigParam defaults, SentimentConfigParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new SentimentConfigParam(
                value(overrides.attentionCenter(), defaults.attentionCenter()),
                value(overrides.attentionScale(), defaults.attentionScale()),
                value(overrides.sentimentHalfLifeHours(), defaults.sentimentHalfLifeHours()),
                value(overrides.newsHalfLifeHours(), defaults.newsHalfLifeHours()),
                value(overrides.policyHalfLifeHours(), defaults.policyHalfLifeHours()),
                merge(defaults.sourceWeights(), overrides.sourceWeights()),
                merge(defaults.shortTermEmotionWeights(), overrides.shortTermEmotionWeights()),
                merge(defaults.panicSellingWeights(), overrides.panicSellingWeights()),
                merge(defaults.sectorRotationWeights(), overrides.sectorRotationWeights()),
                merge(defaults.weakSentimentWeights(), overrides.weakSentimentWeights()),
                merge(defaults.herdingEffectWeights(), overrides.herdingEffectWeights()));
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
                value(overrides.spreadThreshold(), defaults.spreadThreshold()),
                value(overrides.supportDistanceThreshold(), defaults.supportDistanceThreshold()),
                merge(defaults.uncertaintyWeights(), overrides.uncertaintyWeights()));
    }

    private static BreakoutFromRangeConfirmWeightsParam merge(
            BreakoutFromRangeConfirmWeightsParam defaults,
            BreakoutFromRangeConfirmWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new BreakoutFromRangeConfirmWeightsParam(
                value(overrides.baseConfirm(), defaults.baseConfirm()),
                value(overrides.volumeExpand(), defaults.volumeExpand()));
    }

    private static SourceWeightsParam merge(SourceWeightsParam defaults, SourceWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new SourceWeightsParam(
                value(overrides.announcement(), defaults.announcement()),
                value(overrides.mainstreamNews(), defaults.mainstreamNews()),
                value(overrides.research(), defaults.research()),
                value(overrides.socialMedia(), defaults.socialMedia()),
                value(overrides.forum(), defaults.forum()));
    }

    private static ShortTermEmotionWeightsParam merge(
            ShortTermEmotionWeightsParam defaults,
            ShortTermEmotionWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new ShortTermEmotionWeightsParam(
                value(overrides.sentimentPositive(), defaults.sentimentPositive()),
                value(overrides.priceRise(), defaults.priceRise()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.marketAttentionRise(), defaults.marketAttentionRise()));
    }

    private static PanicSellingWeightsParam merge(PanicSellingWeightsParam defaults, PanicSellingWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new PanicSellingWeightsParam(
                value(overrides.priceDrop(), defaults.priceDrop()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.sentimentNegative(), defaults.sentimentNegative()),
                value(overrides.breakRecentLow(), defaults.breakRecentLow()));
    }

    private static SectorRotationWeightsParam merge(
            SectorRotationWeightsParam defaults,
            SectorRotationWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new SectorRotationWeightsParam(
                value(overrides.sectorRelativeStrength(), defaults.sectorRelativeStrength()),
                value(overrides.sectorBreadth(), defaults.sectorBreadth()),
                value(overrides.sectorVolumeExpand(), defaults.sectorVolumeExpand()));
    }

    private static WeakSentimentWeightsParam merge(WeakSentimentWeightsParam defaults, WeakSentimentWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new WeakSentimentWeightsParam(
                value(overrides.sentimentNegative(), defaults.sentimentNegative()),
                value(overrides.priceDrop(), defaults.priceDrop()),
                value(overrides.lowAttention(), defaults.lowAttention()),
                value(overrides.reboundWeak(), defaults.reboundWeak()));
    }

    private static HerdingEffectWeightsParam merge(HerdingEffectWeightsParam defaults, HerdingEffectWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new HerdingEffectWeightsParam(
                value(overrides.marketAttentionRise(), defaults.marketAttentionRise()),
                value(overrides.highTurnover(), defaults.highTurnover()),
                value(overrides.volumeExpand(), defaults.volumeExpand()),
                value(overrides.llmHerdingSignal(), defaults.llmHerdingSignal()));
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
                value(overrides.lowVolume(), defaults.lowVolume()),
                value(overrides.wideSpread(), defaults.wideSpread()));
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
                value(overrides.sentimentNegative(), defaults.sentimentNegative()),
                value(overrides.drawdownRisk(), defaults.drawdownRisk()));
    }

    private static UncertaintyWeightsParam merge(UncertaintyWeightsParam defaults, UncertaintyWeightsParam overrides) {
        if (overrides == null) {
            return defaults;
        }
        return new UncertaintyWeightsParam(
                value(overrides.waitConfirm(), defaults.waitConfirm()),
                value(overrides.sentimentConflict(), defaults.sentimentConflict()),
                value(overrides.policyUncertainty(), defaults.policyUncertainty()),
                value(overrides.newsUncertainty(), defaults.newsUncertainty()));
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
            @JsonProperty("volume_spike_scale") Double volumeSpikeScale,
            @JsonProperty("volume_distribution_source") String volumeDistributionSource,
            @JsonProperty("turnover_distribution_source") String turnoverDistributionSource) {
    }

    public record TrendConfigParam(
            @JsonProperty("rebound_threshold") Double reboundThreshold,
            @JsonProperty("breakout_from_range_confirm_weights")
                    BreakoutFromRangeConfirmWeightsParam breakoutFromRangeConfirmWeights) {
    }

    public record SentimentConfigParam(
            @JsonProperty("attention_center") Double attentionCenter,
            @JsonProperty("attention_scale") Double attentionScale,
            @JsonProperty("sentiment_half_life_hours") Double sentimentHalfLifeHours,
            @JsonProperty("news_half_life_hours") Double newsHalfLifeHours,
            @JsonProperty("policy_half_life_hours") Double policyHalfLifeHours,
            @JsonProperty("source_weights") SourceWeightsParam sourceWeights,
            @JsonProperty("short_term_emotion_weights") ShortTermEmotionWeightsParam shortTermEmotionWeights,
            @JsonProperty("panic_selling_weights") PanicSellingWeightsParam panicSellingWeights,
            @JsonProperty("sector_rotation_weights") SectorRotationWeightsParam sectorRotationWeights,
            @JsonProperty("weak_sentiment_weights") WeakSentimentWeightsParam weakSentimentWeights,
            @JsonProperty("herding_effect_weights") HerdingEffectWeightsParam herdingEffectWeights) {
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
            @JsonProperty("spread_threshold") Double spreadThreshold,
            @JsonProperty("support_distance_threshold") Double supportDistanceThreshold,
            @JsonProperty("uncertainty_weights") UncertaintyWeightsParam uncertaintyWeights) {
    }

    public record BreakoutFromRangeConfirmWeightsParam(
            @JsonProperty("base_confirm") Double baseConfirm,
            @JsonProperty("volume_expand") Double volumeExpand) {
    }

    public record SourceWeightsParam(
            @JsonProperty("announcement") Double announcement,
            @JsonProperty("mainstream_news") Double mainstreamNews,
            @JsonProperty("research") Double research,
            @JsonProperty("social_media") Double socialMedia,
            @JsonProperty("forum") Double forum) {
    }

    public record ShortTermEmotionWeightsParam(
            @JsonProperty("sentiment_positive") Double sentimentPositive,
            @JsonProperty("price_rise") Double priceRise,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("market_attention_rise") Double marketAttentionRise) {
    }

    public record PanicSellingWeightsParam(
            @JsonProperty("price_drop") Double priceDrop,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("sentiment_negative") Double sentimentNegative,
            @JsonProperty("break_recent_low") Double breakRecentLow) {
    }

    public record SectorRotationWeightsParam(
            @JsonProperty("sector_relative_strength") Double sectorRelativeStrength,
            @JsonProperty("sector_breadth") Double sectorBreadth,
            @JsonProperty("sector_volume_expand") Double sectorVolumeExpand) {
    }

    public record WeakSentimentWeightsParam(
            @JsonProperty("sentiment_negative") Double sentimentNegative,
            @JsonProperty("price_drop") Double priceDrop,
            @JsonProperty("low_attention") Double lowAttention,
            @JsonProperty("rebound_weak") Double reboundWeak) {
    }

    public record HerdingEffectWeightsParam(
            @JsonProperty("market_attention_rise") Double marketAttentionRise,
            @JsonProperty("high_turnover") Double highTurnover,
            @JsonProperty("volume_expand") Double volumeExpand,
            @JsonProperty("llm_herding_signal") Double llmHerdingSignal) {
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
            @JsonProperty("low_volume") Double lowVolume,
            @JsonProperty("wide_spread") Double wideSpread) {
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
            @JsonProperty("sentiment_negative") Double sentimentNegative,
            @JsonProperty("drawdown_risk") Double drawdownRisk) {
    }

    public record UncertaintyWeightsParam(
            @JsonProperty("wait_confirm") Double waitConfirm,
            @JsonProperty("sentiment_conflict") Double sentimentConflict,
            @JsonProperty("policy_uncertainty") Double policyUncertainty,
            @JsonProperty("news_uncertainty") Double newsUncertainty) {
    }
}
