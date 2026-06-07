package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.vo.OcrChunkTagDetailVO;
import com.scrapider.finance.ai.domain.vo.OcrStageDetailVO;
import com.scrapider.finance.domain.enums.OcrTaskStageEnum;
import com.scrapider.finance.domain.po.OcrReviewPO;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OcrTaskConverter {

    private OcrTaskConverter() {
    }

    public static OcrStageDetailVO.StageVO toStageVO(OcrTaskStagePO stage) {
        return new OcrStageDetailVO.StageVO(
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
                stage.getUpdatedAt());
    }

    public static OcrStageDetailVO.StageVO reviewStageFallback(OcrReviewPO review) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
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

    public static OcrStageDetailVO stageDetail(String taskNo, List<OcrStageDetailVO.StageVO> stages) {
        return new OcrStageDetailVO(taskNo, stages);
    }

    public static Map<Integer, JsonNode> paragraphsByNo(JsonNode paragraphs) {
        Map<Integer, JsonNode> result = new LinkedHashMap<>();
        paragraphs.forEach(paragraph -> result.put(paragraph.path("paragraphNo").asInt(), paragraph));
        return result;
    }

    public static OcrChunkTagDetailVO toChunkTagDetail(
            String taskNo,
            List<OcrTaskStagePO> stages,
            Map<Integer, JsonNode> finalParagraphs) {
        Map<String, ChunkAccumulator> chunks = new LinkedHashMap<>();
        for (OcrTaskStagePO stage : stages) {
            chunks.computeIfAbsent(stage.getChunkId(), ChunkAccumulator::new).accept(stage);
        }
        List<OcrChunkTagDetailVO.ChunkVO> chunkVos = chunks.values()
                .stream()
                .sorted(Comparator.comparing(ChunkAccumulator::chunkIndex))
                .map(chunk -> chunk.toVO(finalParagraphs.get(chunk.chunkIndex())))
                .toList();
        int totalChunkCount = totalChunkCount(chunkVos, stages);
        int finishedChunkCount = (int) chunkVos.stream().filter(chunk -> "finished".equals(chunk.status())).count();
        int failedChunkCount = (int) chunkVos.stream().filter(chunk -> "failed".equals(chunk.status())).count();
        int llmChunkCount = llmChunkCount(chunkVos, stages);
        int deletedChunkCount = (int) chunkVos.stream().filter(chunk -> Boolean.TRUE.equals(chunk.deleted())).count();
        return new OcrChunkTagDetailVO(
                taskNo,
                totalChunkCount,
                finishedChunkCount,
                failedChunkCount,
                Math.max(totalChunkCount - finishedChunkCount - failedChunkCount, 0),
                llmChunkCount,
                Math.max(totalChunkCount - llmChunkCount, 0),
                deletedChunkCount,
                chunkVos);
    }

    private static int totalChunkCount(List<OcrChunkTagDetailVO.ChunkVO> chunks, List<OcrTaskStagePO> stages) {
        return stages.stream()
                .map(OcrTaskConverter::taskChunkSummary)
                .filter(summary -> summary.has("totalChunkCount"))
                .mapToInt(summary -> summary.path("totalChunkCount").asInt(0))
                .filter(value -> value > 0)
                .findFirst()
                .orElse(chunks.size());
    }

    private static int llmChunkCount(List<OcrChunkTagDetailVO.ChunkVO> chunks, List<OcrTaskStagePO> stages) {
        return stages.stream()
                .map(OcrTaskConverter::taskChunkSummary)
                .filter(summary -> summary.has("llmChunkCount"))
                .mapToInt(summary -> summary.path("llmChunkCount").asInt(0))
                .filter(value -> value > 0)
                .findFirst()
                .orElse((int) chunks.stream().filter(chunk -> Boolean.TRUE.equals(chunk.needLlm())).count());
    }

    private static JsonNode taskChunkSummary(OcrTaskStagePO stage) {
        JsonNode inputMessage = stage.getInputMessage();
        if (inputMessage != null && inputMessage.has("taskChunkSummary")) {
            return inputMessage.path("taskChunkSummary");
        }
        JsonNode outputMessage = stage.getOutputMessage();
        if (outputMessage != null && outputMessage.has("taskChunkSummary")) {
            return outputMessage.path("taskChunkSummary");
        }
        return MissingNode.getInstance();
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
            return MissingNode.getInstance();
        }

        private void copyInts(JsonNode values, List<Integer> target) {
            if (!values.isArray() || !target.isEmpty()) {
                return;
            }
            values.forEach(value -> target.add(value.asInt()));
        }
    }
}
