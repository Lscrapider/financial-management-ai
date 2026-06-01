package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.scrapider.finance.ai.service.OcrTaskMessagePublisher;
import com.scrapider.finance.ai.service.OcrTaskService;
import com.scrapider.finance.domain.enums.OcrTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrReviewManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.OcrTaskStageManage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
        List<OcrStageDetailVO.StageVO> stages = new ArrayList<>(this.ocrTaskStageManage.listTaskStages(normalizedTaskNo)
                .stream()
                .map(stage -> new OcrStageDetailVO.StageVO(
                        stage.getStage(),
                        stage.getStatus(),
                        stage.getAttemptCount(),
                        stage.getMaxAttempts(),
                        stage.getInputRef(),
                        stage.getOutputRef(),
                        stage.getMetrics(),
                        stage.getErrorMessage(),
                        stage.getStartedAt(),
                        stage.getFinishedAt(),
                        stage.getUpdatedAt()))
                .toList());
        this.appendQualityValidateFallback(normalizedTaskNo, stages);
        return new OcrStageDetailVO(normalizedTaskNo, stages);
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
        Map<String, ChunkAccumulator> chunks = new LinkedHashMap<>();
        for (OcrTaskStagePO stage : stages) {
            chunks.computeIfAbsent(stage.getChunkId(), ChunkAccumulator::new).accept(stage);
        }
        Map<Integer, JsonNode> finalParagraphs = this.finalTaggedParagraphs(normalizedTaskNo);
        List<OcrChunkTagDetailVO.ChunkVO> chunkVos = chunks.values()
                .stream()
                .sorted(Comparator.comparing(ChunkAccumulator::chunkIndex))
                .map(chunk -> chunk.toVO(finalParagraphs.get(chunk.chunkIndex())))
                .toList();
        int totalChunkCount = this.totalChunkCount(chunkVos, stages);
        int finishedChunkCount = (int) chunkVos.stream().filter(chunk -> "finished".equals(chunk.status())).count();
        int failedChunkCount = (int) chunkVos.stream().filter(chunk -> "failed".equals(chunk.status())).count();
        int llmChunkCount = this.llmChunkCount(chunkVos, stages);
        int deletedChunkCount = (int) chunkVos.stream().filter(chunk -> Boolean.TRUE.equals(chunk.deleted())).count();
        return new OcrChunkTagDetailVO(
                normalizedTaskNo,
                totalChunkCount,
                finishedChunkCount,
                failedChunkCount,
                Math.max(totalChunkCount - finishedChunkCount - failedChunkCount, 0),
                llmChunkCount,
                Math.max(totalChunkCount - llmChunkCount, 0),
                deletedChunkCount,
                chunkVos);
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
                .map(paragraphs -> {
                    Map<Integer, JsonNode> result = new LinkedHashMap<>();
                    paragraphs.forEach(paragraph -> result.put(paragraph.path("paragraphNo").asInt(), paragraph));
                    return result;
                })
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
        com.fasterxml.jackson.databind.node.ObjectNode metrics =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        metrics.put("paragraphCount", review.getParagraphCount() == null ? 0 : review.getParagraphCount());
        metrics.put("warningCount", review.getWarningCount() == null ? 0 : review.getWarningCount());
        metrics.put("avgConfidence", review.getOverallConfidence() == null
                ? 0
                : review.getOverallConfidence().doubleValue());
        return new OcrStageDetailVO.StageVO(
                OcrTaskStageEnum.QUALITY_VALIDATE.getCode(),
                "finished",
                1,
                3,
                review.getCleanedRef(),
                review.getReviewedRef(),
                metrics,
                null,
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getUpdatedAt());
    }

    private int totalChunkCount(List<OcrChunkTagDetailVO.ChunkVO> chunks, List<OcrTaskStagePO> stages) {
        return stages.stream()
                .map(this::taskChunkSummary)
                .filter(summary -> summary.has("totalChunkCount"))
                .mapToInt(summary -> summary.path("totalChunkCount").asInt(0))
                .filter(value -> value > 0)
                .findFirst()
                .orElse(chunks.size());
    }

    private int llmChunkCount(List<OcrChunkTagDetailVO.ChunkVO> chunks, List<OcrTaskStagePO> stages) {
        return stages.stream()
                .map(this::taskChunkSummary)
                .filter(summary -> summary.has("llmChunkCount"))
                .mapToInt(summary -> summary.path("llmChunkCount").asInt(0))
                .filter(value -> value > 0)
                .findFirst()
                .orElse((int) chunks.stream().filter(chunk -> Boolean.TRUE.equals(chunk.needLlm())).count());
    }

    private JsonNode taskChunkSummary(OcrTaskStagePO stage) {
        JsonNode inputMessage = stage.getInputMessage();
        if (inputMessage != null && inputMessage.has("taskChunkSummary")) {
            return inputMessage.path("taskChunkSummary");
        }
        JsonNode outputMessage = stage.getOutputMessage();
        if (outputMessage != null && outputMessage.has("taskChunkSummary")) {
            return outputMessage.path("taskChunkSummary");
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static final class ChunkAccumulator {

        private static final String STAGE_RULE = OcrTaskStageEnum.CHUNK_TAG_RULE.getCode();
        private static final String STAGE_LLM = OcrTaskStageEnum.CHUNK_TAG_LLM.getCode();
        private static final String STAGE_CORRECT = OcrTaskStageEnum.CHUNK_TAG_CORRECT.getCode();

        private final String chunkId;
        private final List<Integer> pageNos = new ArrayList<>();
        private final List<Integer> paragraphNos = new ArrayList<>();
        private Integer chunkIndex = 0;
        private String text = "";
        private Boolean needLlm = false;
        private String errorMessage;
        private OcrTaskStagePO ruleStage;
        private OcrTaskStagePO llmStage;
        private OcrTaskStagePO correctStage;

        private ChunkAccumulator(String chunkId) {
            this.chunkId = chunkId;
        }

        private void accept(OcrTaskStagePO stage) {
            if (STAGE_RULE.equals(stage.getStage())) {
                this.ruleStage = stage;
            } else if (STAGE_LLM.equals(stage.getStage())) {
                this.llmStage = stage;
            } else if (STAGE_CORRECT.equals(stage.getStage())) {
                this.correctStage = stage;
            }
            this.chunkIndex = stage.getChunkIndex() == null ? this.chunkIndex : stage.getChunkIndex();
            this.errorMessage = stage.getErrorMessage() == null ? this.errorMessage : stage.getErrorMessage();
            JsonNode message = this.messageWithChunk(stage);
            JsonNode chunk = message.path("chunk");
            if (!chunk.isMissingNode()) {
                this.text = chunk.path("text").asText(this.text);
                this.copyInts(chunk.path("pageNos"), this.pageNos);
                this.copyInts(chunk.path("paragraphNos"), this.paragraphNos);
            }
            JsonNode qualityGate = message.path("ruleTagging").path("qualityGate");
            if (!qualityGate.isMissingNode() && qualityGate.has("needLlm")) {
                this.needLlm = qualityGate.path("needLlm").asBoolean(false);
            }
            JsonNode metrics = stage.getMetrics();
            if (metrics != null && metrics.has("needLlm")) {
                this.needLlm = metrics.path("needLlm").asBoolean(false);
            }
        }

        private Integer chunkIndex() {
            return this.chunkIndex == null ? 0 : this.chunkIndex;
        }

        private OcrChunkTagDetailVO.ChunkVO toVO(JsonNode finalParagraph) {
            JsonNode metadata = finalParagraph == null ? null : finalParagraph.path("metadata");
            JsonNode scenes = metadata == null || metadata.isMissingNode() ? this.ruleScenes() : metadata.path("scenes");
            Boolean deleted = metadata != null && !metadata.isMissingNode() && metadata.path("deleted").asBoolean(false);
            return new OcrChunkTagDetailVO.ChunkVO(
                    this.chunkId,
                    this.chunkIndex(),
                    List.copyOf(this.pageNos),
                    List.copyOf(this.paragraphNos),
                    this.status(),
                    this.currentStage(),
                    this.needLlm,
                    deleted,
                    scenes,
                    this.errorMessage,
                    this.text);
        }

        private String status() {
            if (this.hasStatus(this.correctStage, "failed")
                    || this.hasStatus(this.llmStage, "failed")
                    || this.hasStatus(this.ruleStage, "failed")) {
                return "failed";
            }
            if (this.hasStatus(this.correctStage, "finished")) {
                return "finished";
            }
            OcrTaskStagePO current = this.currentStagePO();
            return current == null ? "pending" : current.getStatus();
        }

        private String currentStage() {
            OcrTaskStagePO current = this.currentStagePO();
            return current == null ? STAGE_RULE : current.getStage();
        }

        private OcrTaskStagePO currentStagePO() {
            if (this.correctStage != null) {
                return this.correctStage;
            }
            if (this.llmStage != null) {
                return this.llmStage;
            }
            return this.ruleStage;
        }

        private boolean hasStatus(OcrTaskStagePO stage, String status) {
            return stage != null && status.equals(stage.getStatus());
        }

        private JsonNode ruleScenes() {
            JsonNode message = this.ruleStage == null ? null : this.ruleStage.getOutputMessage();
            if (message != null && message.has("ruleTagging")) {
                return message.path("ruleTagging").path("ruleScenes");
            }
            return null;
        }

        private JsonNode messageWithChunk(OcrTaskStagePO stage) {
            JsonNode inputMessage = stage.getInputMessage();
            if (inputMessage != null && inputMessage.has("chunk")) {
                return inputMessage;
            }
            JsonNode outputMessage = stage.getOutputMessage();
            if (outputMessage != null && outputMessage.has("chunk")) {
                return outputMessage;
            }
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }

        private void copyInts(JsonNode values, List<Integer> target) {
            if (!values.isArray() || !target.isEmpty()) {
                return;
            }
            values.forEach(value -> target.add(value.asInt()));
        }
    }
}
