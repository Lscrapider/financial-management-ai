package com.scrapider.finance.ai.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record OcrQualityValidateMessageDTO(
        String eventId,
        String taskNo,
        String stage,
        Integer attempt,
        OcrStorageRefDTO inputRef,
        Integer paragraphCount,
        JsonNode metrics,
        OcrStorageRefDTO outputPrefix,
        String createdAt) {
}
