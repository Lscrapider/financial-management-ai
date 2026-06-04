package com.scrapider.finance.ai.domain.param;

public record SceneAnalysisSubmitParam(
        String targetType,
        String targetCode,
        String targetName,
        String reportType,
        Integer totalChunks,
        Integer dailyKlineLimit,
        Integer weeklyKlineLimit,
        Integer monthlyKlineLimit,
        String configProfile,
        SceneAnalysisUserConfigParam userOverrides) {
}
