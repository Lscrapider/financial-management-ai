package com.scrapider.finance.ai.domain.param;

import java.util.List;

public record SceneAnalysisCallbackParam(
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload,
        List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
}
