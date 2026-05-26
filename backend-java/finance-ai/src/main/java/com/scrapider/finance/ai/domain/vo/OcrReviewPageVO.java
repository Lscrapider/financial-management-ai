package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record OcrReviewPageVO(
        Integer pageNo,
        JsonNode imageRef,
        String imageUrl) {
}
