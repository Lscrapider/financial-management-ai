package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "scene_analysis_report", autoResultMap = true)
public class SceneAnalysisReportPO {

    private Long id;
    private Long taskId;
    private String taskNo;
    private String targetType;
    private String targetCode;
    private String targetName;
    private String reportType;
    private String generationType;
    private Integer versionNo;
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode reportContent;

    private String reportText;
    private String model;
    private String errorMessage;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SceneAnalysisReportPO createGenerating(
            SceneAnalysisTaskPO task,
            String generationType,
            Integer versionNo) {
        LocalDateTime now = LocalDateTime.now();
        SceneAnalysisReportPO report = new SceneAnalysisReportPO();
        report.setTaskId(task.getId());
        report.setTaskNo(task.getTaskNo());
        report.setTargetType(task.getTargetType());
        report.setTargetCode(task.getTargetCode());
        report.setTargetName(task.getTargetName());
        report.setReportType(task.getReportType());
        report.setGenerationType(generationType);
        report.setVersionNo(versionNo);
        report.setStatus(SceneAnalysisTaskStatusEnum.GENERATING_REPORT.getCode());
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        return report;
    }
}
