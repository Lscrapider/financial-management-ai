package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SceneAnalysisCurrentScenesParam(
        SceneAnalysisSceneModuleParam asset,
        SceneAnalysisSceneModuleParam price,
        SceneAnalysisSceneModuleParam volume,
        SceneAnalysisSceneModuleParam trend,
        SceneAnalysisSceneModuleParam valuation,
        SceneAnalysisSceneModuleParam sentiment,
        @JsonProperty("risk_strategy") SceneAnalysisSceneModuleParam riskStrategy) {

    public SceneAnalysisSceneModuleParam module(String scene) {
        return switch (scene) {
            case "asset" -> this.asset;
            case "price" -> this.price;
            case "volume" -> this.volume;
            case "trend" -> this.trend;
            case "valuation" -> this.valuation;
            case "sentiment" -> this.sentiment;
            case "risk_strategy" -> this.riskStrategy;
            default -> null;
        };
    }
}
