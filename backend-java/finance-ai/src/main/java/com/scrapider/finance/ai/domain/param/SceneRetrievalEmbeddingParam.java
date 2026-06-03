package com.scrapider.finance.ai.domain.param;

import java.util.List;
import java.util.Map;

public record SceneRetrievalEmbeddingParam(
        String scene,
        Integer chunkCount,
        Map<String, Double> currentTags,
        String queryText,
        List<Double> queryEmbedding) {
}
