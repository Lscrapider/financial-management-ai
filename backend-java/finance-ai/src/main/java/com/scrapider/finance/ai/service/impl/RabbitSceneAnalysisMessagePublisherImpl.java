package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import com.scrapider.finance.ai.service.SceneAnalysisMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitSceneAnalysisMessagePublisherImpl implements SceneAnalysisMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String currentSceneRoutingKey;
    private final String retrievalEmbeddingRoutingKey;

    public RabbitSceneAnalysisMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.scene-analysis.rabbitmq.exchange:finance.scene-analysis.topic}") String exchange,
            @Value("${finance.scene-analysis.rabbitmq.current-scene-routing-key:scene.analysis.current.generate}")
                    String currentSceneRoutingKey,
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-routing-key:scene.analysis.retrieval.embedding}")
                    String retrievalEmbeddingRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.currentSceneRoutingKey = currentSceneRoutingKey;
        this.retrievalEmbeddingRoutingKey = retrievalEmbeddingRoutingKey;
    }

    @Override
    public void publishCurrentSceneAnalysisMessage(SceneAnalysisMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.currentSceneRoutingKey, message);
    }

    @Override
    public void publishRetrievalEmbeddingMessage(SceneRetrievalEmbeddingMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.retrievalEmbeddingRoutingKey, message);
    }
}
