package com.scrapider.finance.ai.task;

import com.scrapider.finance.ai.domain.dto.OcrQualityValidateMessageDTO;
import com.scrapider.finance.ai.service.OcrReviewService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OcrQualityValidateMessageListener {

    private final OcrReviewService ocrReviewService;

    public OcrQualityValidateMessageListener(OcrReviewService ocrReviewService) {
        this.ocrReviewService = ocrReviewService;
    }

    @RabbitListener(queues = "${finance.ocr.rabbitmq.quality-validate-queue:finance.ocr.quality.validate}")
    public void handle(OcrQualityValidateMessageDTO message) {
        this.ocrReviewService.initialize(message);
    }
}
