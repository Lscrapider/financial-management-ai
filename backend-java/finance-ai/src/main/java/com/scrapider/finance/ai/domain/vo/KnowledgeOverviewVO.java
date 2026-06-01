package com.scrapider.finance.ai.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeOverviewVO(
        long taskCount,
        long chunkCount,
        long totalTextLength,
        OffsetDateTime latestCreatedAt,
        List<CategoryTagDistribution> tagDistributions) {
}
