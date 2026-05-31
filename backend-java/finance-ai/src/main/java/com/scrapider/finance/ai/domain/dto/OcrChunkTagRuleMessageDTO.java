package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OcrChunkTagRuleMessageDTO(
        String eventId,
        String taskNo,
        String stage,
        Integer attempt,
        OcrStorageRefDTO inputRef,
        OcrStorageRefDTO outputPrefix,
        LocalDateTime createdAt) {

    public static OcrChunkTagRuleMessageDTO create(
            String taskNo,
            OcrStorageRefDTO inputRef,
            OcrStorageRefDTO outputPrefix) {
        return new OcrChunkTagRuleMessageDTO(
                UUID.randomUUID().toString(),
                taskNo,
                "chunk.tag.rule",
                1,
                inputRef,
                outputPrefix,
                LocalDateTime.now());
    }
}
