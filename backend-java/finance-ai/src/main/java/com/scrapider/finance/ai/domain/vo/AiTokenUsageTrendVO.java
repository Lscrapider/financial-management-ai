package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.dto.AiTokenUsageTrendDTO;
import java.time.LocalDateTime;

public record AiTokenUsageTrendVO(
        LocalDateTime timeBucket,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long requestCount) {

    public static AiTokenUsageTrendVO fromDTO(AiTokenUsageTrendDTO dto) {
        return new AiTokenUsageTrendVO(
                dto.getTimeBucket(),
                value(dto.getPromptTokens()),
                value(dto.getCompletionTokens()),
                value(dto.getTotalTokens()),
                value(dto.getRequestCount()));
    }

    private static Long value(Long value) {
        return value == null ? 0L : value;
    }
}
