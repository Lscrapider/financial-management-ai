package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeChunkVO(
        Long id,
        String taskNo,
        String originalFilename,
        Integer chunkIndex,
        String text,
        List<Integer> pageNos,
        List<Integer> paragraphNos,
        Double avgConfidence,
        Integer version,
        JsonNode metadata,
        OffsetDateTime createdAt) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Integer>> INTEGER_LIST_TYPE = new TypeReference<>() {
    };

    public static KnowledgeChunkVO fromPO(KnowledgeVectorPO po) {
        return fromPO(po, null);
    }

    public static KnowledgeChunkVO fromPO(KnowledgeVectorPO po, String originalFilename) {
        JsonNode meta = po.getMetadata();
        List<Integer> pageNos = List.of();
        List<Integer> paragraphNos = List.of();
        Double confidence = null;
        Integer version = null;
        if (meta != null) {
            pageNos = readIntegerList(meta, "pageNos");
            paragraphNos = readIntegerList(meta, "paragraphNos");
            if (meta.has("avgConfidence") && !meta.get("avgConfidence").isNull()) {
                confidence = meta.get("avgConfidence").asDouble();
            }
            if (meta.has("version") && !meta.get("version").isNull()) {
                version = meta.get("version").asInt();
            }
        }
        return new KnowledgeChunkVO(
                po.getId(),
                po.getTaskNo(),
                originalFilename,
                po.getChunkIndex(),
                po.getText(),
                pageNos,
                paragraphNos,
                confidence,
                version,
                meta,
                po.getCreatedAt());
    }

    private static List<Integer> readIntegerList(JsonNode meta, String fieldName) {
        if (!meta.has(fieldName) || !meta.get(fieldName).isArray()) {
            return List.of();
        }
        return List.copyOf(OBJECT_MAPPER.convertValue(meta.get(fieldName), INTEGER_LIST_TYPE));
    }
}
