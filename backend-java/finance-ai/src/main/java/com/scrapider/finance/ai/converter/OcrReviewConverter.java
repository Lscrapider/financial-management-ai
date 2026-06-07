package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.OcrStorageRefDTO;
import com.scrapider.finance.ai.domain.vo.OcrReviewPageVO;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.domain.po.OcrReviewPO;
import java.time.LocalDateTime;
import java.util.List;

public final class OcrReviewConverter {

    private OcrReviewConverter() {
    }

    public static OcrReviewVO toVO(OcrReviewPO review, List<OcrReviewPageVO> pages) {
        return new OcrReviewVO(
                review.getTaskNo(),
                review.getStatus(),
                review.getOverallConfidence(),
                review.getParagraphCount(),
                review.getWarningCount(),
                review.getDraftContent(),
                pages);
    }

    public static JsonNode reviewedContent(ObjectMapper objectMapper, String taskNo, JsonNode finalContent) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("taskNo", taskNo);
        root.put("reviewedAt", LocalDateTime.now().toString());
        root.set("content", finalContent);
        return root;
    }

    public static OcrStorageRefDTO minioRef(String bucket, String objectKey) {
        return new OcrStorageRefDTO("minio", bucket, objectKey);
    }

    public static OcrReviewPageVO toPageVO(String taskNo, JsonNode page) {
        int pageNo = page.path("pageNo").asInt();
        return new OcrReviewPageVO(
                pageNo,
                page.path("imageRef"),
                "/api/ai/ocr/reviews/" + taskNo + "/pages/" + pageNo + "/image");
    }
}
