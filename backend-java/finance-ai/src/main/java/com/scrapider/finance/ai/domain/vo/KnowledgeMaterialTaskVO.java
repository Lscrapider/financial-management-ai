package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record KnowledgeMaterialTaskVO(
        String taskNo,
        String searchMode,
        String targetType,
        String targetCode,
        String targetName,
        String queryText,
        String rewrittenQuery,
        String status,
        String errorMessage,
        JsonNode currentScenesPayload,
        JsonNode knowledgeContext,
        List<KnowledgeMaterialChunkVO> chunks,
        String submittedAt,
        String finishedAt) {
}
