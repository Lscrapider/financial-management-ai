package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.enums.OcrReviewStatusEnum;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.mapper.OcrReviewMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OcrReviewManage extends ServiceImpl<OcrReviewMapper, OcrReviewPO> {

    private final ObjectMapper objectMapper;

    public OcrReviewManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void initializePendingByTaskNo(OcrReviewPO review) {
        this.baseMapper.upsertPending(
                review.getTaskNo(),
                review.getStatus(),
                this.toJson(review.getCleanedRef()),
                this.toJson(review.getDraftContent()),
                review.getOverallConfidence(),
                review.getParagraphCount(),
                review.getWarningCount(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    public Optional<OcrReviewPO> findByTaskNo(String taskNo) {
        return this.lambdaQuery()
                .eq(OcrReviewPO::getTaskNo, taskNo)
                .oneOpt();
    }

    public void saveDraft(String taskNo, JsonNode draftContent) {
        this.baseMapper.updateDraft(
                taskNo,
                OcrReviewStatusEnum.SAVED.getCode(),
                this.toJson(draftContent),
                LocalDateTime.now());
    }

    public void saveManualDraft(OcrReviewPO review) {
        this.baseMapper.updateManualDraft(
                review.getTaskNo(),
                OcrReviewStatusEnum.SAVED.getCode(),
                this.toJson(review.getCleanedRef()),
                this.toJson(review.getDraftContent()),
                review.getOverallConfidence(),
                review.getParagraphCount(),
                review.getWarningCount(),
                LocalDateTime.now());
    }

    public void approve(String taskNo, JsonNode reviewedRef) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.approve(
                taskNo,
                OcrReviewStatusEnum.APPROVED.getCode(),
                this.toJson(reviewedRef),
                now,
                now);
    }

    private String toJson(JsonNode node) {
        try {
            return this.objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OCR 复核 JSON 序列化失败", ex);
        }
    }
}
