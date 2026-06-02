package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record SceneAnalysisCallbackParam(
        String status,
        JsonNode currentScenesPayload,
        JsonNode reportPayload,
        String reportText,
        String errorMessage) {
}
