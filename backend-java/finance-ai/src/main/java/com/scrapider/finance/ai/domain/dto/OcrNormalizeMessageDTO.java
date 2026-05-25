package com.scrapider.finance.ai.domain.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OcrNormalizeMessageDTO(
        String eventId,
        String eventType,
        String taskNo,
        String stage,
        String storageType,
        String bucket,
        String objectKey,
        String originalFilename,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt) {

    public static OcrNormalizeMessageDTO create(
            String taskNo,
            String bucket,
            String objectKey,
            String originalFilename,
            String contentType,
            Long fileSize) {
        return new OcrNormalizeMessageDTO(
                UUID.randomUUID().toString(),
                "ocr.document.normalize.requested",
                taskNo,
                "document.normalize",
                "minio",
                bucket,
                objectKey,
                originalFilename,
                contentType,
                fileSize,
                LocalDateTime.now());
    }
}
