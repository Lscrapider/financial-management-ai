package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.time.LocalDateTime;

public record AiTokenUsageLogVO(
        Long id,
        String provider,
        String responseId,
        String objectType,
        Long userId,
        String username,
        String source,
        String phase,
        String model,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer cachedTokens,
        Integer reasoningTokens,
        Integer promptCacheHitTokens,
        Integer promptCacheMissTokens,
        AiTokenUsageCostVO estimatedCost,
        LocalDateTime occurredAt,
        LocalDateTime createdAt) {

    public static AiTokenUsageLogVO fromPO(AiTokenUsageLogPO po) {
        return fromPO(po, null);
    }

    public static AiTokenUsageLogVO fromPO(AiTokenUsageLogPO po, String username) {
        return fromPO(po, username, null);
    }

    public static AiTokenUsageLogVO fromPO(
            AiTokenUsageLogPO po,
            String username,
            AiTokenUsageCostVO estimatedCost) {
        return new AiTokenUsageLogVO(
                po.getId(),
                po.getProvider(),
                po.getResponseId(),
                po.getObjectType(),
                po.getUserId(),
                username,
                po.getSource(),
                po.getPhase(),
                po.getModel(),
                po.getFinishReason(),
                po.getPromptTokens(),
                po.getCompletionTokens(),
                po.getTotalTokens(),
                po.getCachedTokens(),
                po.getReasoningTokens(),
                po.getPromptCacheHitTokens(),
                po.getPromptCacheMissTokens(),
                estimatedCost,
                po.getOccurredAt(),
                po.getCreatedAt());
    }
}
