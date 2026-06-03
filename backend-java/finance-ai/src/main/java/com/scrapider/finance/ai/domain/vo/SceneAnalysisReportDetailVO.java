package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record SceneAnalysisReportDetailVO(
        Long reportId,
        Long taskId,
        String taskNo,
        String targetType,
        String targetCode,
        String targetName,
        String reportType,
        String generationType,
        Integer versionNo,
        String status,
        JsonNode reportContent,
        String reportText,
        String model,
        String errorMessage,
        String generatedAt,
        String createdAt) {
}
