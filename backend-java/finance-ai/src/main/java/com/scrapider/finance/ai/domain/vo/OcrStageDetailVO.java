package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public record OcrStageDetailVO(
        String taskNo,
        List<StageVO> stages) {

    public record StageVO(
            String stage,
            String status,
            Integer attemptCount,
            Integer maxAttempts,
            JsonNode inputRef,
            JsonNode outputRef,
            JsonNode metrics,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime updatedAt) {
    }
}
