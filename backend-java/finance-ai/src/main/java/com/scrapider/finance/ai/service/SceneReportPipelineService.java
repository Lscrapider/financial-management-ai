package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import java.util.List;

public interface SceneReportPipelineService {

    void start(String taskNo, SceneAnalysisCurrentScenesPayloadParam currentScenesPayload);

    void continueWithRetrievalEmbeddings(String taskNo, List<SceneRetrievalEmbeddingParam> retrievalEmbeddings);
}
