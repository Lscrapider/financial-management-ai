package com.scrapider.finance.ai.domain.dto;

import java.util.List;

public record SceneRetrievalEmbeddingMessageDTO(
        String taskNo,
        String stage,
        List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks) {

    public static SceneRetrievalEmbeddingMessageDTO create(
            String taskNo,
            List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks) {
        return new SceneRetrievalEmbeddingMessageDTO(
                taskNo,
                "scene.retrieval.embedding",
                retrievalTasks);
    }
}
