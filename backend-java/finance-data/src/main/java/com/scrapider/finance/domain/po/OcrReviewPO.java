package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.OcrReviewStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "ocr_review", autoResultMap = true)
public class OcrReviewPO {

    private Long id;
    private String taskNo;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode cleanedRef;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode reviewedRef;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode draftContent;

    private BigDecimal overallConfidence;
    private Integer paragraphCount;
    private Integer warningCount;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OcrReviewPO createPending(
            String taskNo,
            JsonNode cleanedRef,
            JsonNode draftContent,
            BigDecimal overallConfidence,
            Integer paragraphCount,
            Integer warningCount) {
        LocalDateTime now = LocalDateTime.now();
        OcrReviewPO review = new OcrReviewPO();
        review.setTaskNo(taskNo);
        review.setStatus(OcrReviewStatusEnum.PENDING.getCode());
        review.setCleanedRef(cleanedRef);
        review.setDraftContent(draftContent);
        review.setOverallConfidence(overallConfidence);
        review.setParagraphCount(paragraphCount);
        review.setWarningCount(warningCount);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        return review;
    }
}
