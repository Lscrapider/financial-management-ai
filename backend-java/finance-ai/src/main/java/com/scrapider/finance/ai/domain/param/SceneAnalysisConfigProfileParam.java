package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record SceneAnalysisConfigProfileParam(
        String name,
        String configGroup,
        String targetType,
        String reportType,
        JsonNode configJson) {
}
