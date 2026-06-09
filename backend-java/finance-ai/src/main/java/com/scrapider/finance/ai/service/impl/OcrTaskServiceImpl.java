package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.OcrTaskConverter;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;
import com.scrapider.finance.ai.domain.dto.StoredOcrFileDTO;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.vo.OcrChunkTagDetailVO;
import com.scrapider.finance.ai.domain.vo.OcrStageDetailVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.domain.enums.OcrTaskStageEnum;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.ai.service.OcrFileStorageService;
import com.scrapider.finance.ai.publisher.OcrTaskMessagePublisher;
import com.scrapider.finance.ai.service.OcrTaskService;
import com.scrapider.finance.domain.enums.OcrTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.OcrTaskStageManage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrTaskServiceImpl implements OcrTaskService {

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final OcrTaskManage ocrTaskManage;
    private final OcrTaskStageManage ocrTaskStageManage;
    private final OcrReviewManage ocrReviewManage;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrFileStorageService ocrFileStorageService;
    private final OcrTaskMessagePublisher ocrTaskMessagePublisher;

    public OcrTaskServiceImpl(
            OcrTaskManage ocrTaskManage,
            OcrTaskStageManage ocrTaskStageManage,
            OcrReviewManage ocrReviewManage,
            KnowledgeVectorManage knowledgeVectorManage,
            OcrFileStorageService ocrFileStorageService,
            OcrTaskMessagePublisher ocrTaskMessagePublisher) {
        this.ocrTaskManage = ocrTaskManage;
        this.ocrTaskStageManage = ocrTaskStageManage;
        this.ocrReviewManage = ocrReviewManage;
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.ocrFileStorageService = ocrFileStorageService;
        this.ocrTaskMessagePublisher = ocrTaskMessagePublisher;
    }

    @Override
    public List<OcrTaskVO> submit(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        files.forEach(this::validateFile);
        return files.stream().map(this::submitOne).toList();
    }

    @Override
    public OcrTaskPageVO page(OcrTaskPageParam param) {
        OcrTaskPageParam query = param == null ? new OcrTaskPageParam() : param;
        int pageNum = this.normalizePageNum(query.getPageNum());
        int pageSize = this.normalizePageSize(query.getPageSize());
        OcrTaskStatusEnum taskStatus = OcrTaskStatusEnum.fromCode(query.getStatus());
        Page<OcrTaskPO> page = this.ocrTaskManage.pageTasks(pageNum, pageSize, taskStatus);
        return OcrTaskPageVO.fromPage(page);
    }

    @Override
    public OcrStageDetailVO stageDetail(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        String normalizedTaskNo = taskNo.trim();
        List<OcrStageDetailVO.StageVO> stages = this.ocrTaskStageManage.listTaskStages(normalizedTaskNo)
                .stream()
                .map(OcrTaskConverter::toStageVO)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        this.appendQualityValidateFallback(normalizedTaskNo, stages);
        return OcrTaskConverter.stageDetail(normalizedTaskNo, stages);
    }

    @Override
    public OcrChunkTagDetailVO chunkTagDetail(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        String normalizedTaskNo = taskNo.trim();
        List<OcrTaskStagePO> stages = this.ocrTaskStageManage.listChunkStages(
                normalizedTaskNo,
                Set.of(
                        OcrTaskStageEnum.CHUNK_TAG_RULE.getCode(),
                        OcrTaskStageEnum.CHUNK_TAG_LLM.getCode(),
                        OcrTaskStageEnum.CHUNK_TAG_CORRECT.getCode()));
        Map<Integer, JsonNode> finalParagraphs = this.finalTaggedParagraphs(normalizedTaskNo);
        return OcrTaskConverter.toChunkTagDetail(normalizedTaskNo, stages, finalParagraphs);
    }

    @Override
    public void delete(OcrTaskDeleteParam param) {
        if (param == null || param.taskNo() == null || param.taskNo().isBlank()) {
            throw new IllegalArgumentException("任务编号不能为空");
        }
        boolean deleted = this.ocrTaskManage.softDelete(param.taskNo().trim());
        if (!deleted) {
            throw new IllegalArgumentException("OCR 任务不存在或已删除");
        }
        this.knowledgeVectorManage.deleteByTaskNo(param.taskNo().trim());
    }

    private OcrTaskVO submitOne(MultipartFile file) {
        String originalFilename = this.originalFilename(file);
        String fileType = this.fileType(originalFilename);
        String taskNo = "ocr-" + UUID.randomUUID().toString().replace("-", "");
        StoredOcrFileDTO storedFile = this.ocrFileStorageService.saveOriginalFile(taskNo, fileType, file);

        OcrTaskPO task = OcrTaskPO.createReady(
                taskNo,
                originalFilename,
                storedFile.storedFilename(),
                storedFile.storageUri(),
                fileType,
                file.getContentType(),
                file.getSize());
        OcrTaskPO savedTask = this.ocrTaskManage.saveTask(task);
        this.ocrTaskMessagePublisher.publishNormalizeMessage(OcrNormalizeMessageDTO.create(
                taskNo,
                storedFile.bucket(),
                storedFile.objectKey(),
                originalFilename,
                file.getContentType(),
                file.getSize()));
        return OcrTaskVO.fromPO(savedTask);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("上传文件不能超过50MB");
        }
        String fileType = this.fileType(this.originalFilename(file));
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("仅支持 PDF、PNG、JPG、JPEG、WEBP 文件");
        }
    }

    private String originalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String fileType(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
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

    private Map<Integer, JsonNode> finalTaggedParagraphs(String taskNo) {
        return this.ocrTaskStageManage
                .findByTaskNoAndStage(taskNo, OcrTaskStageEnum.CHUNK_TAG_CORRECT.getCode())
                .map(OcrTaskStagePO::getOutputRef)
                .filter(ref -> ref != null && !ref.isNull())
                .map(ref -> this.ocrFileStorageService.readJson(
                        ref.path("bucket").asText(),
                        ref.path("objectKey").asText()))
                .map(content -> content.path("content").path("paragraphs"))
                .filter(JsonNode::isArray)
                .map(OcrTaskConverter::paragraphsByNo)
                .orElseGet(Map::of);
    }

    private void appendQualityValidateFallback(String taskNo, List<OcrStageDetailVO.StageVO> stages) {
        boolean exists = stages.stream()
                .anyMatch(stage -> OcrTaskStageEnum.QUALITY_VALIDATE.getCode().equals(stage.stage()));
        if (exists) {
            return;
        }
        this.ocrReviewManage.findByTaskNo(taskNo).ifPresent(review -> stages.add(this.reviewStageFallback(review)));
    }

    private OcrStageDetailVO.StageVO reviewStageFallback(OcrReviewPO review) {
        return OcrTaskConverter.reviewStageFallback(review);
    }
}
