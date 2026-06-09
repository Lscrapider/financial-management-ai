package com.scrapider.finance.ai.publisher;

import com.scrapider.finance.ai.domain.dto.KnowledgeReembedMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrChunkTagRuleMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrEmbeddingIndexMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String normalizeRoutingKey;
    private final String chunkTagRuleRoutingKey;
    private final String embeddingIndexRoutingKey;
    private final String reembedRoutingKey;

    public OcrTaskMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.ocr.rabbitmq.exchange:finance.ocr.topic}") String exchange,
            @Value("${finance.ocr.rabbitmq.normalize-routing-key:ocr.document.normalize}") String normalizeRoutingKey,
            @Value("${finance.ocr.rabbitmq.chunk-tag-rule-routing-key:ocr.chunk.tag.rule}") String chunkTagRuleRoutingKey,
            @Value("${finance.ocr.rabbitmq.embedding-index-routing-key:ocr.embedding.index}") String embeddingIndexRoutingKey,
            @Value("${finance.ocr.rabbitmq.reembed-routing-key:knowledge.chunk.reembed}") String reembedRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.normalizeRoutingKey = normalizeRoutingKey;
        this.chunkTagRuleRoutingKey = chunkTagRuleRoutingKey;
        this.embeddingIndexRoutingKey = embeddingIndexRoutingKey;
        this.reembedRoutingKey = reembedRoutingKey;
    }

    public void publishNormalizeMessage(OcrNormalizeMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.normalizeRoutingKey, message);
    }

    public void publishChunkTagRuleMessage(OcrChunkTagRuleMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.chunkTagRuleRoutingKey, message);
    }

    public void publishEmbeddingIndexMessage(OcrEmbeddingIndexMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.embeddingIndexRoutingKey, message);
    }

    public void publishReembedMessage(KnowledgeReembedMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.reembedRoutingKey, message);
    }
}
