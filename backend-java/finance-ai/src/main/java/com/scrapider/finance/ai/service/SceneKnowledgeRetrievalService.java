package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import java.util.List;
import java.util.Map;

public interface SceneKnowledgeRetrievalService {

    List<SceneChunkAllocationDTO> allocateChunks(SceneAnalysisCurrentScenesPayloadParam payload);

    List<SceneKnowledgeRetrievalTaskDTO> buildRetrievalTasks(
            List<SceneChunkAllocationDTO> allocations,
            SceneAnalysisCurrentScenesPayloadParam payload);

    List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks(List<SceneRetrievalEmbeddingParam> retrievalEmbeddings);

    Map<String, List<SceneKnowledgeChunkDTO>> retrieveKnowledge(
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings);
}
