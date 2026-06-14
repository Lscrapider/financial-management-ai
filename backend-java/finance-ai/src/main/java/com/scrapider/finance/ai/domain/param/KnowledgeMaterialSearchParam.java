package com.scrapider.finance.ai.domain.param;

public record KnowledgeMaterialSearchParam(
        String searchMode,
        String targetType,
        String targetCode,
        String targetName,
        String queryText,
        String reportType,
        Integer totalChunks,
        Integer dailyKlineLimit,
        Integer weeklyKlineLimit,
        Integer monthlyKlineLimit,
        String configProfile,
        SceneAnalysisUserConfigParam userOverrides) {
}
