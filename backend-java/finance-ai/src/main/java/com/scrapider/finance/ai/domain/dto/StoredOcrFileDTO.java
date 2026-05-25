package com.scrapider.finance.ai.domain.dto;

public record StoredOcrFileDTO(
        String storageType,
        String bucket,
        String objectKey,
        String storageUri,
        String storedFilename) {
}
