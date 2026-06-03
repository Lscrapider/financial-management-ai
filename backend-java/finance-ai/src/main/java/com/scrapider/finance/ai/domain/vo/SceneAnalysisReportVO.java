package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record SceneAnalysisReportVO(
        String taskNo,
        Long reportId,
        String status,
        String errorMessage,
        String generationType,
        Integer versionNo,
        JsonNode reportContent,
        String reportText,
        String model,
        String generatedAt) {
}
