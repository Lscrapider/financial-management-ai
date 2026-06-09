package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.dto.AiTokenUsageSummaryDTO;
import java.time.LocalDateTime;

public record AiTokenUsageOverviewVO(
        Long requestCount,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long cachedTokens,
        Long reasoningTokens,
        AiTokenUsageCostVO estimatedCost,
        LocalDateTime latestOccurredAt) {

    public static AiTokenUsageOverviewVO fromDTO(AiTokenUsageSummaryDTO dto) {
        return fromDTO(dto, null);
    }

    public static AiTokenUsageOverviewVO fromDTO(AiTokenUsageSummaryDTO dto, AiTokenUsageCostVO estimatedCost) {
        if (dto == null) {
            return new AiTokenUsageOverviewVO(0L, 0L, 0L, 0L, 0L, 0L, estimatedCost, null);
        }
        return new AiTokenUsageOverviewVO(
                value(dto.getRequestCount()),
                value(dto.getPromptTokens()),
                value(dto.getCompletionTokens()),
                value(dto.getTotalTokens()),
                value(dto.getCachedTokens()),
                value(dto.getReasoningTokens()),
                estimatedCost,
                dto.getLatestOccurredAt());
    }

    private static Long value(Long value) {
        return value == null ? 0L : value;
    }
}
