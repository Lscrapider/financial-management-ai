package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "scene_analysis_task", autoResultMap = true)
public class SceneAnalysisTaskPO {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    private Long id;
    private String taskNo;
    private Long userId;
    private String targetType;
    private String targetCode;
    private String targetName;
    private String reportType;
    private String configProfile;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode configSnapshot;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode currentScenesPayload;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode reportPayload;

    private String reportText;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SceneAnalysisTaskPO createPending(
            String taskNo,
            Long userId,
            String targetType,
            String targetCode,
            String targetName,
            String reportType,
            String configProfile,
            JsonNode configSnapshot) {
        LocalDateTime now = LocalDateTime.now();
        SceneAnalysisTaskPO task = new SceneAnalysisTaskPO();
        task.setTaskNo(taskNo);
        task.setUserId(userId);
        task.setTargetType(targetType);
        task.setTargetCode(targetCode);
        task.setTargetName(targetName);
        task.setReportType(reportType);
        task.setConfigProfile(configProfile);
        task.setConfigSnapshot(configSnapshot);
        task.setStatus(STATUS_PENDING);
        task.setSubmittedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
