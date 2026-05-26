package com.scrapider.finance.ai.domain.dto;

public record OcrStorageRefDTO(
        String storageType,
        String bucket,
        String objectKey) {
}
