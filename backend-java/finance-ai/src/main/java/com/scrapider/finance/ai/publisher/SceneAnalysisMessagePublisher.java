package com.scrapider.finance.ai.publisher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisMessagePublisher {

    private static final String CALLBACK_TOKEN_FIELD = "callbackToken";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String currentSceneRoutingKey;
    private final String retrievalEmbeddingRoutingKey;

    public SceneAnalysisMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${finance.scene-analysis.rabbitmq.exchange:finance.scene-analysis.topic}") String exchange,
            @Value("${finance.scene-analysis.rabbitmq.current-scene-routing-key:scene.analysis.current.generate}")
                    String currentSceneRoutingKey,
            @Value("${finance.scene-analysis.rabbitmq.retrieval-embedding-routing-key:scene.analysis.retrieval.embedding}")
                    String retrievalEmbeddingRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.currentSceneRoutingKey = currentSceneRoutingKey;
        this.retrievalEmbeddingRoutingKey = retrievalEmbeddingRoutingKey;
    }

    public void publishCurrentSceneAnalysisMessage(SceneAnalysisMessageDTO message, String callbackToken) {
        this.publishCurrentSceneAnalysisMessage(message, callbackToken, null);
    }

    public void publishCurrentSceneAnalysisMessage(
            SceneAnalysisMessageDTO message,
            String callbackToken,
            String callbackPath) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.currentSceneRoutingKey,
                this.withCallbackToken(message, callbackToken, callbackPath));
    }

    public void publishRetrievalEmbeddingMessage(SceneRetrievalEmbeddingMessageDTO message, String callbackToken) {
        this.publishRetrievalEmbeddingMessage(message, callbackToken, null);
    }

    public void publishRetrievalEmbeddingMessage(
            SceneRetrievalEmbeddingMessageDTO message,
            String callbackToken,
            String callbackPath) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.retrievalEmbeddingRoutingKey,
                this.withCallbackToken(message, callbackToken, callbackPath));
    }

    private Map<String, Object> withCallbackToken(Object message, String callbackToken, String callbackPath) {
        Map<String, Object> payload = new LinkedHashMap<>(this.objectMapper.convertValue(message, MAP_TYPE));
        payload.put(CALLBACK_TOKEN_FIELD, callbackToken);
        if (callbackPath != null && !callbackPath.isBlank()) {
            payload.put("callbackPath", callbackPath);
        }
        return payload;
    }
}
