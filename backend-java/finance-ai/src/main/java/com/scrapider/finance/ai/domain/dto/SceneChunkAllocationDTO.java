package com.scrapider.finance.ai.domain.dto;

public record SceneChunkAllocationDTO(
        String scene,
        Integer chunkCount,
        Double score,
        Double effectiveScore) {
}
