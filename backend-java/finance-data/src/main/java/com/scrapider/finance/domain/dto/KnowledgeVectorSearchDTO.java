package com.scrapider.finance.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class KnowledgeVectorSearchDTO {

    private Long id;
    private String taskNo;
    private Integer chunkIndex;
    private String text;
    private JsonNode metadata;
    private Double semanticScore;
}
