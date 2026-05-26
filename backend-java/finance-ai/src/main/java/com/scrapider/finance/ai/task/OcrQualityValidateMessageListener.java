package com.scrapider.finance.ai.task;

import com.scrapider.finance.ai.domain.dto.OcrQualityValidateMessageDTO;
import com.scrapider.finance.ai.service.OcrReviewInitializationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OcrQualityValidateMessageListener {

    private final OcrReviewInitializationService ocrReviewInitializationService;

    public OcrQualityValidateMessageListener(OcrReviewInitializationService ocrReviewInitializationService) {
        this.ocrReviewInitializationService = ocrReviewInitializationService;
    }

    @RabbitListener(queues = "${finance.ocr.rabbitmq.quality-validate-queue:finance.ocr.quality.validate}")
    public void handle(OcrQualityValidateMessageDTO message) {
        this.ocrReviewInitializationService.initialize(message);
    }
}
