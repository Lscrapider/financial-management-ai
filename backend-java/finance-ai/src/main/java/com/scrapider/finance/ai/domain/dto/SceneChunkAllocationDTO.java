package com.scrapider.finance.ai.domain.dto;

public record SceneChunkAllocationDTO(
        String category,
        Integer chunkCount,
        Double score,
        Double effectiveScore) {
}
