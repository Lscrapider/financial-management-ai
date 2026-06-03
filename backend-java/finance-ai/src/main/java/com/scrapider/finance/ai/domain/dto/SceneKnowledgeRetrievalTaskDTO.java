package com.scrapider.finance.ai.domain.dto;

import java.util.Map;

public record SceneKnowledgeRetrievalTaskDTO(
        String scene,
        Integer chunkCount,
        Map<String, Double> currentTags,
        String queryText) {
}
