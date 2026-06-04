package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record SceneAnalysisCurrentScenesPayloadParam(
        SceneAnalysisCurrentScenesTargetParam target,
        String reportType,
        Integer totalChunks,
        JsonNode marketContext,
        SceneAnalysisCurrentScenesParam currentScenes) {
}
