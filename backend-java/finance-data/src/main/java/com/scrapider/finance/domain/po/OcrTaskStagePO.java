package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.config.JsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "ocr_task_stage", autoResultMap = true)
public class OcrTaskStagePO {

    private Long id;
    private String taskNo;
    private String stage;
    private String chunkId;
    private Integer chunkIndex;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode inputMessage;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode outputMessage;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode inputRef;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode outputRef;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode metrics;

    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
