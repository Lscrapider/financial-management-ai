package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.OcrEmbeddingIndexMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrStorageRefDTO;
import com.scrapider.finance.ai.domain.param.OcrReviewDraftParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewPageVO;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.service.OcrReviewService;
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.enums.OcrTaskStageEnum;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.OcrTaskStageManage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OcrReviewServiceImpl implements OcrReviewService {

    private final OcrReviewManage ocrReviewManage;
    private final OcrTaskStageManage ocrTaskStageManage;
    private final OcrFileStorageService ocrFileStorageService;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;
    private final OcrTaskManage ocrTaskManage;
    private final ObjectMapper objectMapper;

    public OcrReviewServiceImpl(
            OcrReviewManage ocrReviewManage,
            OcrTaskStageManage ocrTaskStageManage,
            OcrFileStorageService ocrFileStorageService,
            OcrTaskMessagePublisher ocrTaskMessagePublisher,
            OcrTaskManage ocrTaskManage,
            ObjectMapper objectMapper) {
        this.ocrReviewManage = ocrReviewManage;
        this.ocrTaskStageManage = ocrTaskStageManage;
        this.ocrFileStorageService = ocrFileStorageService;
        this.ocrTaskMessagePublisher = ocrTaskMessagePublisher;
        this.ocrTaskManage = ocrTaskManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public OcrReviewVO detail(String taskNo) {
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseGet(() -> this.initializeFromTextCleanStage(taskNo));
        return new OcrReviewVO(
                review.getTaskNo(),
                review.getStatus(),
                review.getOverallConfidence(),
                review.getParagraphCount(),
                review.getWarningCount(),
                review.getDraftContent(),
                this.listPages(taskNo));
    }

    @Override
    public void saveDraft(String taskNo, OcrReviewDraftParam param) {
        if (param == null || param.draftContent() == null || param.draftContent().isNull()) {
            throw new IllegalArgumentException("复核草稿不能为空");
        }
        this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("OCR 复核任务不存在"));
        this.ocrReviewManage.saveDraft(taskNo, param.draftContent());
    }

    @Override
    public void submit(String taskNo, OcrReviewDraftParam param) {
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("OCR 复核任务不存在"));
        JsonNode finalContent = param != null && param.draftContent() != null && !param.draftContent().isNull()
                ? param.draftContent()
                : review.getDraftContent();
        if (finalContent == null || finalContent.isNull()) {
            throw new IllegalArgumentException("复核提交内容不能为空");
        }
        int reviewedParagraphCount = this.paragraphCount(finalContent);

        JsonNode reviewedContent = this.reviewedContent(taskNo, finalContent);
        JsonNode cleanedRef = review.getCleanedRef();
        String bucket = cleanedRef.path("bucket").asText();
        String reviewedObjectKey = this.stageOutputPrefix(taskNo, 4) + "/review/reviewed.json";
        this.ocrFileStorageService.writeJson(bucket, reviewedObjectKey, reviewedContent);

        OcrStorageRefDTO reviewedRef = new OcrStorageRefDTO("minio", bucket, reviewedObjectKey);
        OcrStorageRefDTO embeddingOutputPrefix = new OcrStorageRefDTO(
                "minio",
                bucket,
                this.stageOutputPrefix(taskNo, 5) + "/embedding/");
        this.ocrReviewManage.approve(taskNo, this.objectMapper.valueToTree(reviewedRef));
        this.ocrTaskManage.markEmbeddingIndexRunning(taskNo, reviewedParagraphCount);
        this.ocrTaskMessagePublisher.publishEmbeddingIndexMessage(
                OcrEmbeddingIndexMessageDTO.create(taskNo, reviewedRef, embeddingOutputPrefix));
    }

    @Override
    public byte[] pageImage(String taskNo, Integer pageNo) {
        JsonNode page = this.findPage(taskNo, pageNo);
        JsonNode imageRef = page.path("imageRef");
        return this.ocrFileStorageService.readBytes(
                imageRef.path("bucket").asText(),
                imageRef.path("objectKey").asText());
    }

    private List<OcrReviewPageVO> listPages(String taskNo) {
        JsonNode pages = this.documentNormalizeOutputMessage(taskNo).path("pages");
        List<OcrReviewPageVO> result = new ArrayList<>();
        if (!pages.isArray()) {
            return result;
        }
        pages.forEach(page -> result.add(new OcrReviewPageVO(
                page.path("pageNo").asInt(),
                page.path("imageRef"),
                "/api/ai/ocr/reviews/" + taskNo + "/pages/" + page.path("pageNo").asInt() + "/image")));
        return result;
    }

    private JsonNode findPage(String taskNo, Integer pageNo) {
        if (pageNo == null) {
            throw new IllegalArgumentException("页码不能为空");
        }
        JsonNode pages = this.documentNormalizeOutputMessage(taskNo).path("pages");
        if (pages.isArray()) {
            for (JsonNode page : pages) {
                if (page.path("pageNo").asInt() == pageNo) {
                    return page;
                }
            }
        }
        throw new IllegalArgumentException("OCR 页面不存在");
    }

    private JsonNode documentNormalizeOutputMessage(String taskNo) {
        OcrTaskStagePO stage = this.ocrTaskStageManage
                .findByTaskNoAndStage(taskNo, OcrTaskStageEnum.DOCUMENT_NORMALIZE.getCode())
                .orElseThrow(() -> new IllegalArgumentException("OCR 标准化阶段记录不存在"));
        return stage.getOutputMessage();
    }

    private OcrReviewPO initializeFromTextCleanStage(String taskNo) {
        OcrTaskStagePO stage = this.ocrTaskStageManage
                .findByTaskNoAndStage(taskNo, OcrTaskStageEnum.TEXT_CLEAN.getCode())
                .orElseThrow(() -> new IllegalArgumentException("OCR 复核任务不存在"));
        JsonNode outputRef = stage.getOutputRef();
        if (outputRef == null || outputRef.isNull()) {
            throw new IllegalArgumentException("OCR 文本清洗产物不存在");
        }
        JsonNode cleanedContent = this.ocrFileStorageService.readJson(
                outputRef.path("bucket").asText(),
                outputRef.path("objectKey").asText());
        JsonNode metrics = cleanedContent.path("metrics");
        OcrReviewPO review = OcrReviewPO.createPending(
                taskNo,
                outputRef,
                cleanedContent,
                metrics.path("avgConfidence").decimalValue(),
                cleanedContent.path("paragraphCount").asInt(0),
                metrics.path("warningCount").asInt(0));
        this.ocrReviewManage.initializePendingByTaskNo(review);
        this.ocrTaskManage.markManualReviewRequired(taskNo);
        return this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("OCR 复核任务初始化失败"));
    }

    private JsonNode reviewedContent(String taskNo, JsonNode finalContent) {
        ObjectNode root = this.objectMapper.createObjectNode();
        root.put("taskNo", taskNo);
        root.put("reviewedAt", LocalDateTime.now().toString());
        root.set("content", finalContent);
        return root;
    }

    private int paragraphCount(JsonNode finalContent) {
        JsonNode paragraphs = finalContent.path("paragraphs");
        if (!paragraphs.isArray()) {
            throw new IllegalArgumentException("复核提交段落不能为空");
        }
        return paragraphs.size();
    }

    private String stageOutputPrefix(String taskNo, int stageNo) {
        LocalDate today = LocalDate.now();
        return String.format(
                "stage-%d-output/%d/%02d/%02d/%s",
                stageNo,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                taskNo);
    }
}
