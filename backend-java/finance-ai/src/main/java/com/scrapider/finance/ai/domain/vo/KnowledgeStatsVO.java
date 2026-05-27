package com.scrapider.finance.ai.domain.vo;

import java.time.OffsetDateTime;

public record KnowledgeStatsVO(
        long taskCount,
        long chunkCount,
        long totalTextLength,
        OffsetDateTime latestCreatedAt) {
}
