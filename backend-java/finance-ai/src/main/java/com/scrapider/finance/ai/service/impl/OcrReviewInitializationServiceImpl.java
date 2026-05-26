package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.OcrQualityValidateMessageDTO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.service.OcrReviewInitializationService;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class OcrReviewInitializationServiceImpl implements OcrReviewInitializationService {

    private final OcrFileStorageService ocrFileStorageService;
    private final OcrReviewManage ocrReviewManage;
    private final OcrTaskManage ocrTaskManage;
    private final ObjectMapper objectMapper;

    public OcrReviewInitializationServiceImpl(
            OcrFileStorageService ocrFileStorageService,
            OcrReviewManage ocrReviewManage,
            OcrTaskManage ocrTaskManage,
            ObjectMapper objectMapper) {
        this.ocrFileStorageService = ocrFileStorageService;
        this.ocrReviewManage = ocrReviewManage;
        this.ocrTaskManage = ocrTaskManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public void initialize(OcrQualityValidateMessageDTO message) {
        if (message == null || message.inputRef() == null) {
            throw new IllegalArgumentException("OCR 质量校验消息不能为空");
        }
        JsonNode cleanedContent = this.ocrFileStorageService.readJson(
                message.inputRef().bucket(),
                message.inputRef().objectKey());
        JsonNode metrics = cleanedContent.path("metrics");
        OcrReviewPO review = OcrReviewPO.createPending(
                message.taskNo(),
                this.toJsonNode(message.inputRef()),
                cleanedContent,
                this.decimal(metrics.path("avgConfidence")),
                this.intValue(cleanedContent.path("paragraphCount")),
                this.intValue(metrics.path("warningCount")));
        this.ocrReviewManage.initializePendingByTaskNo(review);
        this.ocrTaskManage.markManualReviewRequired(message.taskNo());
    }

    private JsonNode toJsonNode(Object value) {
        return this.objectMapper.valueToTree(value);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return node.decimalValue();
    }

    private int intValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        return node.asInt(0);
    }
}
