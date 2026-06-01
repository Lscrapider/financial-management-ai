package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import com.scrapider.finance.mapper.OcrTaskStageMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OcrTaskStageManage extends ServiceImpl<OcrTaskStageMapper, OcrTaskStagePO> {

    private final ObjectMapper objectMapper;

    public OcrTaskStageManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void startTaskStage(
            String taskNo,
            String stage,
            Integer attemptCount,
            Integer maxAttempts,
            JsonNode inputMessage,
            JsonNode inputRef) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.upsertTaskStageRunning(
                taskNo,
                stage,
                attemptCount,
                maxAttempts,
                this.toJson(inputMessage),
                this.toJson(inputRef),
                now,
                now,
                now);
    }

    public void finishTaskStage(
            String taskNo,
            String stage,
            JsonNode outputRef,
            JsonNode outputMessage,
            JsonNode metrics) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.finishTaskStage(
                taskNo,
                stage,
                this.toJson(outputRef),
                this.toJson(outputMessage),
                this.toJson(metrics),
                now,
                now);
    }

    public void failTaskStage(String taskNo, String stage, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.failTaskStage(taskNo, stage, errorMessage, now, now);
    }

    public Optional<OcrTaskStagePO> findByTaskNoAndStage(String taskNo, String stage) {
        return this.lambdaQuery()
                .eq(OcrTaskStagePO::getTaskNo, taskNo)
                .eq(OcrTaskStagePO::getStage, stage)
                .isNull(OcrTaskStagePO::getChunkId)
                .oneOpt();
    }

    public List<OcrTaskStagePO> listTaskStages(String taskNo) {
        return this.lambdaQuery()
                .eq(OcrTaskStagePO::getTaskNo, taskNo)
                .isNull(OcrTaskStagePO::getChunkId)
                .orderByAsc(OcrTaskStagePO::getId)
                .list();
    }

    public List<OcrTaskStagePO> listChunkStages(String taskNo, Collection<String> stages) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .eq(OcrTaskStagePO::getTaskNo, taskNo)
                .in(OcrTaskStagePO::getStage, stages)
                .isNotNull(OcrTaskStagePO::getChunkId)
                .orderByAsc(OcrTaskStagePO::getChunkIndex)
                .orderByAsc(OcrTaskStagePO::getStage)
                .list();
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return this.objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OCR 阶段 JSON 序列化失败", ex);
        }
    }
}
