package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record KnowledgeReembedMessageDTO(
        String eventId,
        String chunkId,
        String newText,
        LocalDateTime createdAt) {

    public static KnowledgeReembedMessageDTO create(String chunkId, String newText) {
        return new KnowledgeReembedMessageDTO(
                UUID.randomUUID().toString(),
                chunkId,
                newText,
                LocalDateTime.now());
    }
}
