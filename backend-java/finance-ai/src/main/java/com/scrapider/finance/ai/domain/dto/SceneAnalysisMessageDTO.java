package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SceneAnalysisMessageDTO(
        String taskNo,
        LocalDateTime requestedAt,
        String reportType,
        SceneAnalysisTargetDTO target,
        SceneAnalysisConfigDTO config,
        Map<String, Object> marketData,
        Map<String, Object> valuationData,
        List<Map<String, Object>> dailyKlines,
        List<Map<String, Object>> intradayData,
        Map<String, Object> watchlistState,
        Map<String, Object> alertState,
        Map<String, Object> dataCompleteness) {
}
