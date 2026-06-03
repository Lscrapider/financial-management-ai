package com.scrapider.finance.ai.domain.vo;

public record SceneAnalysisReportHistoryVO(
        Long reportId,
        String taskNo,
        String targetType,
        String targetCode,
        String targetName,
        String reportType,
        String generationType,
        Integer versionNo,
        String status,
        String model,
        String errorMessage,
        String generatedAt,
        String createdAt) {
}
