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
        LocalDateTime latestOccurredAt) {

    public static AiTokenUsageOverviewVO fromDTO(AiTokenUsageSummaryDTO dto) {
        if (dto == null) {
            return new AiTokenUsageOverviewVO(0L, 0L, 0L, 0L, 0L, 0L, null);
        }
        return new AiTokenUsageOverviewVO(
                value(dto.getRequestCount()),
                value(dto.getPromptTokens()),
                value(dto.getCompletionTokens()),
                value(dto.getTotalTokens()),
                value(dto.getCachedTokens()),
                value(dto.getReasoningTokens()),
                dto.getLatestOccurredAt());
    }

    private static Long value(Long value) {
        return value == null ? 0L : value;
    }
}
