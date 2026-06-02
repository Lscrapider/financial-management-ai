package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.mapper.SceneAnalysisTaskMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisTaskManage extends ServiceImpl<SceneAnalysisTaskMapper, SceneAnalysisTaskPO> {

    private final ObjectMapper objectMapper;

    public SceneAnalysisTaskManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SceneAnalysisTaskPO saveTask(SceneAnalysisTaskPO task) {
        this.baseMapper.insertTask(
                task.getTaskNo(),
                task.getUserId(),
                task.getTargetType(),
                task.getTargetCode(),
                task.getTargetName(),
                task.getReportType(),
                task.getConfigProfile(),
                this.toJson(task.getConfigSnapshot()),
                task.getStatus(),
                task.getSubmittedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());
        return task;
    }

    public void markProcessing(String taskNo) {
        LocalDateTime now = LocalDateTime.now();
        this.lambdaUpdate()
                .eq(SceneAnalysisTaskPO::getTaskNo, taskNo)
                .set(SceneAnalysisTaskPO::getStatus, SceneAnalysisTaskPO.STATUS_PROCESSING)
                .set(SceneAnalysisTaskPO::getStartedAt, now)
                .set(SceneAnalysisTaskPO::getUpdatedAt, now)
                .update();
    }

    public void markSuccess(
            String taskNo,
            JsonNode currentScenesPayload,
            JsonNode reportPayload,
            String reportText) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.markSuccess(
                taskNo,
                SceneAnalysisTaskPO.STATUS_SUCCESS,
                this.toJson(currentScenesPayload),
                this.toJson(reportPayload),
                reportText,
                now,
                now);
    }

    public void markFailed(String taskNo, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        this.lambdaUpdate()
                .eq(SceneAnalysisTaskPO::getTaskNo, taskNo)
                .set(SceneAnalysisTaskPO::getStatus, SceneAnalysisTaskPO.STATUS_FAILED)
                .set(SceneAnalysisTaskPO::getErrorMessage, errorMessage)
                .set(SceneAnalysisTaskPO::getFinishedAt, now)
                .set(SceneAnalysisTaskPO::getUpdatedAt, now)
                .update();
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "{}";
        }
        try {
            return this.objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize scene analysis json payload", ex);
        }
    }
}
