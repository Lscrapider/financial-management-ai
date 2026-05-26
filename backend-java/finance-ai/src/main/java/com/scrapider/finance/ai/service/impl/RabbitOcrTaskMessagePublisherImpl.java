package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.OcrEmbeddingIndexMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RabbitOcrTaskMessagePublisherImpl implements OcrTaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String normalizeRoutingKey;
    private final String embeddingIndexRoutingKey;

    public RabbitOcrTaskMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.ocr.rabbitmq.exchange:finance.ocr.topic}") String exchange,
            @Value("${finance.ocr.rabbitmq.normalize-routing-key:ocr.document.normalize}") String normalizeRoutingKey,
            @Value("${finance.ocr.rabbitmq.embedding-index-routing-key:ocr.embedding.index}") String embeddingIndexRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.normalizeRoutingKey = normalizeRoutingKey;
        this.embeddingIndexRoutingKey = embeddingIndexRoutingKey;
    }

    @Override
    public void publishNormalizeMessage(OcrNormalizeMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.normalizeRoutingKey, message);
    }

    @Override
    public void publishEmbeddingIndexMessage(OcrEmbeddingIndexMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.embeddingIndexRoutingKey, message);
    }
}
