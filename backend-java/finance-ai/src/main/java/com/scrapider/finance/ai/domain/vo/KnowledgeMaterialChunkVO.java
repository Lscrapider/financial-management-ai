package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record KnowledgeMaterialChunkVO(
        Long chunkId,
        String taskNo,
        Integer chunkIndex,
        String scene,
        String filename,
        String text,
        List<String> matchedTags,
        Double semanticScore,
        Double tagMatchScore,
        Double crossSceneScore,
        Double finalScore) {
}
