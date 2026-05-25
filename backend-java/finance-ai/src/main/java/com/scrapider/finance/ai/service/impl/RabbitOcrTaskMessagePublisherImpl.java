package com.scrapider.finance.ai.service.impl;

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

    public RabbitOcrTaskMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${finance.ocr.rabbitmq.exchange:finance.ocr.topic}") String exchange,
            @Value("${finance.ocr.rabbitmq.normalize-routing-key:ocr.document.normalize}") String normalizeRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.normalizeRoutingKey = normalizeRoutingKey;
    }

    @Override
    public void publishNormalizeMessage(OcrNormalizeMessageDTO message) {
        this.rabbitTemplate.convertAndSend(this.exchange, this.normalizeRoutingKey, message);
    }
}
