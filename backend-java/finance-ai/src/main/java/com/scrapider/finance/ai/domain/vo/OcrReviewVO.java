package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public record OcrReviewVO(
        String taskNo,
        String status,
        BigDecimal overallConfidence,
        Integer paragraphCount,
        Integer warningCount,
        JsonNode draftContent,
        List<OcrReviewPageVO> pages) {
}
