package com.scrapider.finance.ai.domain.vo;

public record SceneAnalysisReportTargetVO(
        String targetType,
        String targetCode,
        String targetName,
        Long latestReportId,
        String latestTaskNo,
        String latestStatus,
        String latestReportType,
        String latestGenerationType,
        Integer latestVersionNo,
        String latestModel,
        String latestReportPreview,
        String latestGeneratedAt,
        String latestCreatedAt,
        Long reportCount) {
}
