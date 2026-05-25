package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.time.LocalDateTime;

public record AiTokenUsageLogVO(
        Long id,
        String provider,
        String responseId,
        String model,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer cachedTokens,
        Integer reasoningTokens,
        LocalDateTime occurredAt) {

    public static AiTokenUsageLogVO fromPO(AiTokenUsageLogPO po) {
        return new AiTokenUsageLogVO(
                po.getId(),
                po.getProvider(),
                po.getResponseId(),
                po.getModel(),
                po.getFinishReason(),
                po.getPromptTokens(),
                po.getCompletionTokens(),
                po.getTotalTokens(),
                po.getCachedTokens(),
                po.getReasoningTokens(),
                po.getOccurredAt());
    }
}
