package com.scrapider.finance.ai.domain.param;

public record SceneAnalysisSubmitParam(
        String targetType,
        String targetCode,
        String targetName,
        String reportType,
        String configProfile,
        SceneAnalysisUserConfigParam userOverrides) {
}
