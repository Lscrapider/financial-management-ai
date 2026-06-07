package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SceneReportPipelineConverter {

    private SceneReportPipelineConverter() {
    }

    public static SceneChunkAllocationDTO allocation(
            String scene,
            int chunkCount,
            double score,
            double effectiveScore) {
        return new SceneChunkAllocationDTO(scene, chunkCount, score, effectiveScore);
    }

    public static SceneKnowledgeRetrievalTaskDTO retrievalTask(
            String scene,
            int chunkCount,
            Map<String, Double> currentTags,
            String queryText) {
        return new SceneKnowledgeRetrievalTaskDTO(scene, chunkCount, currentTags, queryText);
    }

    public static SceneKnowledgeChunkDTO knowledgeChunk(
            KnowledgeVectorSearchDTO row,
            String scene,
            List<String> matchedTags,
            double semanticScore,
            double tagMatchScore,
            double crossSceneScore,
            double finalScore) {
        return new SceneKnowledgeChunkDTO(
                row.getId(),
                row.getTaskNo(),
                row.getChunkIndex(),
                scene,
                row.getText(),
                matchedTags,
                semanticScore,
                tagMatchScore,
                crossSceneScore,
                finalScore);
    }

    public static Map<String, List<SceneKnowledgeChunkDTO>> knowledgeContext() {
        return new LinkedHashMap<>();
    }

    public static void putSceneChunks(
            Map<String, List<SceneKnowledgeChunkDTO>> context,
            String scene,
            List<SceneKnowledgeChunkDTO> chunks) {
        context.put(scene, chunks);
    }
}
