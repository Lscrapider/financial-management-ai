package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "knowledge_vector", autoResultMap = true)
public class KnowledgeVectorPO {

    private Long id;
    private String taskNo;
    private Integer chunkIndex;
    private String text;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode metadata;

    private OffsetDateTime createdAt;
}
