package com.scrapider.finance.ai.domain.dto;

import java.time.Instant;

public record ConversationCleanupMessageDTO(
        String type,
        Long userId,
        String conversationId,
        Long cleanupVersion,
        Instant scheduledAt,
        Integer delayMinutes) {

    public static ConversationCleanupMessageDTO of(
            Long userId,
            String conversationId,
            Long cleanupVersion,
            int delayMinutes) {
        return new ConversationCleanupMessageDTO(
                "conversation.cleanup",
                userId,
                conversationId,
                cleanupVersion,
                Instant.now(),
                delayMinutes);
    }
}
