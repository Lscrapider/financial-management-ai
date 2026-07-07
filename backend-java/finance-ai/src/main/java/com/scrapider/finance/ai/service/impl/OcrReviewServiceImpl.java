package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.OcrReviewConverter;
import com.scrapider.finance.ai.domain.dto.OcrChunkTagRuleMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrQualityValidateMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrStorageRefDTO;
import com.scrapider.finance.ai.domain.param.OcrReviewDraftParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewPageVO;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.service.OcrReviewService;
import com.scrapider.finance.ai.publisher.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.enums.OcrTaskStageEnum;
import com.scrapider.finance.domain.exception.BusinessException;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.OcrTaskStageManage;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    public void initialize(OcrQualityValidateMessageDTO message) {
        if (message == null || message.inputRef() == null) {
            throw new BusinessException("OCR 质量校验消息不能为空。");
        }
        String stage = OcrTaskStageEnum.QUALITY_VALIDATE.getCode();
        this.ocrTaskStageManage.startTaskStage(
                message.taskNo(),
                stage,
                message.attempt() == null ? 1 : message.attempt(),
                3,
                this.toJsonNode(message),
                this.toJsonNode(message.inputRef()));
        try {
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
            this.ocrTaskStageManage.finishTaskStage(
                    message.taskNo(),
                    stage,
                    this.toJsonNode(message.outputPrefix()),
                    this.toJsonNode(message),
                    metrics);
        } catch (RuntimeException ex) {
            this.ocrTaskStageManage.failTaskStage(message.taskNo(), stage, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public OcrReviewVO detail(String taskNo) {
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseGet(() -> this.initializeFromTextCleanStage(taskNo));
        return OcrReviewConverter.toVO(review, this.listPages(taskNo));
    }

    @Override
    public void saveDraft(String taskNo, OcrReviewDraftParam param) {
        if (param == null || param.draftContent() == null || param.draftContent().isNull()) {
            throw new BusinessException("复核草稿不能为空。");
        }
        this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("OCR 复核任务不存在。"));
        this.ocrReviewManage.saveDraft(taskNo, param.draftContent());
    }

    @Override
    public void submit(String taskNo, OcrReviewDraftParam param) {
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("OCR 复核任务不存在。"));
        JsonNode finalContent = param != null && param.draftContent() != null && !param.draftContent().isNull()
                ? param.draftContent()
                : review.getDraftContent();
        if (finalContent == null || finalContent.isNull()) {
            throw new BusinessException("复核提交内容不能为空。");
        }
        int reviewedParagraphCount = this.paragraphCount(finalContent);

        JsonNode reviewedContent = OcrReviewConverter.reviewedContent(this.objectMapper, taskNo, finalContent);
        JsonNode cleanedRef = review.getCleanedRef();
        String bucket = cleanedRef.path("bucket").asText();
        String reviewedObjectKey = this.stageOutputPrefix(taskNo, 4) + "/review/reviewed.json";
        this.ocrFileStorageService.writeJson(bucket, reviewedObjectKey, reviewedContent);

        OcrStorageRefDTO reviewedRef = OcrReviewConverter.minioRef(bucket, reviewedObjectKey);
        OcrStorageRefDTO chunkTagOutputPrefix = OcrReviewConverter.minioRef(
                bucket,
                this.stageOutputPrefix(taskNo, 5) + "/chunk-tag/");
        this.ocrReviewManage.approve(taskNo, this.objectMapper.valueToTree(reviewedRef));
        this.ocrTaskManage.markChunkTagRuleRunning(taskNo, reviewedParagraphCount);
        this.ocrTaskMessagePublisher.publishChunkTagRuleMessage(
                OcrChunkTagRuleMessageDTO.create(taskNo, reviewedRef, chunkTagOutputPrefix));
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
        pages.forEach(page -> result.add(OcrReviewConverter.toPageVO(taskNo, page)));
        return result;
    }

    private JsonNode findPage(String taskNo, Integer pageNo) {
        if (pageNo == null) {
            throw new BusinessException("页码不能为空。");
        }
        JsonNode pages = this.documentNormalizeOutputMessage(taskNo).path("pages");
        if (pages.isArray()) {
            for (JsonNode page : pages) {
                if (page.path("pageNo").asInt() == pageNo) {
                    return page;
                }
            }
        }
        throw new BusinessException("OCR 页面不存在。");
    }

    private JsonNode documentNormalizeOutputMessage(String taskNo) {
        OcrTaskStagePO stage = this.ocrTaskStageManage
                .findByTaskNoAndStage(taskNo, OcrTaskStageEnum.DOCUMENT_NORMALIZE.getCode())
                .orElseThrow(() -> new BusinessException("OCR 标准化阶段记录不存在。"));
        return stage.getOutputMessage();
    }

    private OcrReviewPO initializeFromTextCleanStage(String taskNo) {
        OcrTaskStagePO stage = this.ocrTaskStageManage
                .findByTaskNoAndStage(taskNo, OcrTaskStageEnum.TEXT_CLEAN.getCode())
                .orElseThrow(() -> new BusinessException("OCR 复核任务不存在。"));
        JsonNode outputRef = stage.getOutputRef();
        if (outputRef == null || outputRef.isNull()) {
            throw new BusinessException("OCR 文本清洗产物不存在。");
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
                .orElseThrow(() -> new BusinessException("OCR 复核任务初始化失败。"));
    }

    private int paragraphCount(JsonNode finalContent) {
        JsonNode paragraphs = finalContent.path("paragraphs");
        if (!paragraphs.isArray()) {
            throw new BusinessException("复核提交段落不能为空。");
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
