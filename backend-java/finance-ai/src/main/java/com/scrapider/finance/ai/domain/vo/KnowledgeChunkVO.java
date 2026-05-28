package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
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
            if (meta.has("pageNos") && meta.get("pageNos").isArray()) {
                pageNos = List.copyOf(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .convertValue(meta.get("pageNos"), List.class));
            }
            if (meta.has("paragraphNos") && meta.get("paragraphNos").isArray()) {
                paragraphNos = List.copyOf(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .convertValue(meta.get("paragraphNos"), List.class));
            }
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
}
