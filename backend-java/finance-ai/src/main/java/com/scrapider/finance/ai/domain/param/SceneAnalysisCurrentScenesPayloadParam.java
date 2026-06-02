package com.scrapider.finance.ai.domain.param;

public record SceneAnalysisCurrentScenesPayloadParam(
        SceneAnalysisCurrentScenesTargetParam target,
        String reportType,
        Integer totalChunks,
        SceneAnalysisCurrentScenesParam currentScenes) {
}
