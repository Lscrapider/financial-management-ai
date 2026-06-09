package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.ManualKnowledgeConverter;
import com.scrapider.finance.ai.converter.OcrReviewConverter;
import com.scrapider.finance.ai.domain.dto.OcrChunkTagRuleMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrStorageRefDTO;
import com.scrapider.finance.ai.domain.param.ManualKnowledgeDraftParam;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.service.ManualKnowledgeService;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.publisher.OcrTaskMessagePublisher;
import com.scrapider.finance.domain.enums.OcrTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ManualKnowledgeServiceImpl implements ManualKnowledgeService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String SOURCE_TYPE = "manual_text";

    private final OcrTaskManage ocrTaskManage;
    private final OcrReviewManage ocrReviewManage;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrFileStorageService ocrFileStorageService;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;
    private final ObjectMapper objectMapper;
    private final String bucket;

    public ManualKnowledgeServiceImpl(
            OcrTaskManage ocrTaskManage,
            OcrReviewManage ocrReviewManage,
            KnowledgeVectorManage knowledgeVectorManage,
            OcrFileStorageService ocrFileStorageService,
            OcrTaskMessagePublisher ocrTaskMessagePublisher,
            ObjectMapper objectMapper,
            @Value("${finance.minio.ocr-bucket}") String bucket) {
        this.ocrTaskManage = ocrTaskManage;
        this.ocrReviewManage = ocrReviewManage;
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.ocrFileStorageService = ocrFileStorageService;
        this.ocrTaskMessagePublisher = ocrTaskMessagePublisher;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
    }

    @Override
    public OcrTaskPageVO page(OcrTaskPageParam param) {
        OcrTaskPageParam query = param == null ? new OcrTaskPageParam() : param;
        int pageNum = this.normalizePageNum(query.getPageNum());
        int pageSize = this.normalizePageSize(query.getPageSize());
        OcrTaskStatusEnum taskStatus = OcrTaskStatusEnum.fromCode(query.getStatus());
        Page<OcrTaskPO> page = this.ocrTaskManage.pageTasks(pageNum, pageSize, taskStatus, SOURCE_TYPE);
        return OcrTaskPageVO.fromPage(page);
    }

    @Override
    public OcrTaskVO createDraft(ManualKnowledgeDraftParam param) {
        List<String> chunks = this.validChunks(param);
        String taskNo = "manual-" + UUID.randomUUID().toString().replace("-", "");
        String title = this.title(param, chunks);
        JsonNode draftContent = ManualKnowledgeConverter.draftContent(this.objectMapper, taskNo, chunks);
        OcrStorageRefDTO cleanedRef = this.writeCleanedJson(taskNo, draftContent);
        OcrReviewPO review = OcrReviewPO.createPending(
                taskNo,
                this.objectMapper.valueToTree(cleanedRef),
                draftContent,
                BigDecimal.ONE,
                chunks.size(),
                0);
        OcrTaskPO task = this.ocrTaskManage.saveTask(OcrTaskPO.createManualText(taskNo, title, chunks.size()));
        this.ocrReviewManage.initializePendingByTaskNo(review);
        return OcrTaskVO.fromPO(task);
    }

    @Override
    public OcrReviewVO detail(String taskNo) {
        this.manualTask(taskNo);
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("手动导入草稿不存在"));
        return OcrReviewConverter.toVO(review, List.of());
    }

    @Override
    public void saveDraft(String taskNo, ManualKnowledgeDraftParam param) {
        this.editableManualTask(taskNo);
        List<String> chunks = this.validChunks(param);
        this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("手动导入草稿不存在"));
        String title = this.title(param, chunks);
        JsonNode draftContent = ManualKnowledgeConverter.draftContent(this.objectMapper, taskNo, chunks);
        OcrStorageRefDTO cleanedRef = this.writeCleanedJson(taskNo, draftContent);
        OcrReviewPO review = OcrReviewPO.createPending(
                taskNo,
                this.objectMapper.valueToTree(cleanedRef),
                draftContent,
                BigDecimal.ONE,
                chunks.size(),
                0);
        this.ocrReviewManage.saveManualDraft(review);
        this.ocrTaskManage.updateManualDraft(taskNo, title, chunks.size());
    }

    @Override
    public void submit(String taskNo, ManualKnowledgeDraftParam param) {
        this.editableManualTask(taskNo);
        this.saveDraft(taskNo, param);
        OcrReviewPO review = this.ocrReviewManage.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("手动导入草稿不存在"));
        JsonNode reviewedContent = OcrReviewConverter.reviewedContent(this.objectMapper, taskNo, review.getDraftContent());
        String reviewedObjectKey = this.stageOutputPrefix(taskNo, 4) + "/review/reviewed.json";
        this.ocrFileStorageService.writeJson(this.bucket, reviewedObjectKey, reviewedContent);

        OcrStorageRefDTO reviewedRef = OcrReviewConverter.minioRef(this.bucket, reviewedObjectKey);
        OcrStorageRefDTO chunkTagOutputPrefix = OcrReviewConverter.minioRef(
                this.bucket,
                this.stageOutputPrefix(taskNo, 5) + "/chunk-tag/");
        int paragraphCount = review.getDraftContent().path("paragraphs").size();
        this.ocrReviewManage.approve(taskNo, this.objectMapper.valueToTree(reviewedRef));
        this.ocrTaskManage.markChunkTagRuleRunning(taskNo, paragraphCount);
        this.ocrTaskMessagePublisher.publishChunkTagRuleMessage(
                OcrChunkTagRuleMessageDTO.create(taskNo, reviewedRef, chunkTagOutputPrefix));
    }

    @Override
    public void delete(OcrTaskDeleteParam param) {
        if (param == null || param.taskNo() == null || param.taskNo().isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        boolean deleted = this.ocrTaskManage.softDelete(param.taskNo().trim(), SOURCE_TYPE);
        if (!deleted) {
            throw new IllegalArgumentException("手动导入任务不存在或已删除");
        }
        this.knowledgeVectorManage.deleteByTaskNo(param.taskNo().trim());
    }

    private OcrTaskPO manualTask(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        return this.ocrTaskManage.findActiveByTaskNoAndSourceType(taskNo.trim(), SOURCE_TYPE)
                .orElseThrow(() -> new IllegalArgumentException("手动导入任务不存在或已删除"));
    }

    private OcrTaskPO editableManualTask(String taskNo) {
        OcrTaskPO task = this.manualTask(taskNo);
        if (!OcrTaskStatusEnum.MANUAL_REVIEW_REQUIRED.getCode().equals(task.getStatus())) {
            throw new IllegalArgumentException("仅草稿待提交的手动任务可以编辑");
        }
        return task;
    }

    private List<String> validChunks(ManualKnowledgeDraftParam param) {
        if (param == null || param.chunks() == null) {
            throw new IllegalArgumentException("文本分段不能为空");
        }
        List<String> chunks = param.chunks()
                .stream()
                .map(text -> text == null ? "" : text.trim())
                .filter(text -> !text.isBlank())
                .toList();
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个非空文本分段");
        }
        return chunks;
    }

    private String title(ManualKnowledgeDraftParam param, List<String> chunks) {
        String title = param == null ? "" : param.title();
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String first = chunks.get(0);
        return first.length() > 5 ? first.substring(0, 5) + "..." : first;
    }

    private OcrStorageRefDTO writeCleanedJson(String taskNo, JsonNode draftContent) {
        String objectKey = this.stageOutputPrefix(taskNo, 3) + "/text/clean/cleaned.json";
        this.ocrFileStorageService.writeJson(this.bucket, objectKey, draftContent);
        return OcrReviewConverter.minioRef(this.bucket, objectKey);
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

    private int normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
