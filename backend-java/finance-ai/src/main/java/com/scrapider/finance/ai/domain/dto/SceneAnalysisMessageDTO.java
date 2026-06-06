package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SceneAnalysisMessageDTO(
        String taskNo,
        LocalDateTime requestedAt,
        String reportType,
        Integer totalChunks,
        SceneAnalysisTargetDTO target,
        SceneAnalysisConfigDTO config,
        Map<String, Object> marketData,
        Map<String, Object> valuationData,
        Map<String, Object> industryData,
        List<Map<String, Object>> valuationHistory,
        List<Map<String, Object>> financialIndicators,
        List<Map<String, Object>> dividendHistory,
        List<Map<String, Object>> dailyKlines,
        List<Map<String, Object>> weeklyKlines,
        List<Map<String, Object>> monthlyKlines,
        List<Map<String, Object>> intradayData,
        Map<String, Object> assetSpecificData,
        Map<String, Object> dataCompleteness) {
}
