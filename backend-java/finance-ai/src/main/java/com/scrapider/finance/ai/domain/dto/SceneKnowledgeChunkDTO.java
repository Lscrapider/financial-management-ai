package com.scrapider.finance.ai.domain.dto;

import java.util.List;

public record SceneKnowledgeChunkDTO(
        Long chunkId,
        String taskNo,
        Integer chunkIndex,
        String scene,
        String text,
        List<String> matchedTags,
        Double semanticScore,
        Double tagMatchScore,
        Double crossSceneScore,
        Double finalScore) {
}
