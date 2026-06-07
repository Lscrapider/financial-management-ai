package com.scrapider.finance.domain.vo;

public record MarketSyncJobVO(
        String jobNo,
        String targetType,
        String syncMode,
        String dataScope,
        String triggerType,
        String targetCode,
        String status,
        String startedAt,
        String finishedAt,
        Long durationMs,
        String errorMessage) {
}
