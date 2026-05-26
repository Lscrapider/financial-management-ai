package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OcrEmbeddingIndexMessageDTO(
        String eventId,
        String taskNo,
        String stage,
        Integer attempt,
        OcrStorageRefDTO inputRef,
        OcrStorageRefDTO outputPrefix,
        LocalDateTime createdAt) {

    public static OcrEmbeddingIndexMessageDTO create(
            String taskNo,
            OcrStorageRefDTO inputRef,
            OcrStorageRefDTO outputPrefix) {
        return new OcrEmbeddingIndexMessageDTO(
                UUID.randomUUID().toString(),
                taskNo,
                "embedding.index",
                1,
                inputRef,
                outputPrefix,
                LocalDateTime.now());
    }
}
