package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;

public interface SceneAnalysisMessagePublisher {

    void publishCurrentSceneAnalysisMessage(SceneAnalysisMessageDTO message);

    void publishRetrievalEmbeddingMessage(SceneRetrievalEmbeddingMessageDTO message);
}
