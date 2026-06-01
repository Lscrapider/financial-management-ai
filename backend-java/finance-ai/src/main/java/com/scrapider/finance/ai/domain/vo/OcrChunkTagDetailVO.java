package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record OcrChunkTagDetailVO(
        String taskNo,
        Integer totalChunkCount,
        Integer finishedChunkCount,
        Integer failedChunkCount,
        Integer pendingChunkCount,
        Integer llmChunkCount,
        Integer ruleOnlyChunkCount,
        Integer deletedChunkCount,
        List<ChunkVO> chunks) {

    public record ChunkVO(
            String chunkId,
            Integer chunkIndex,
            List<Integer> pageNos,
            List<Integer> paragraphNos,
            String status,
            String currentStage,
            Boolean needLlm,
            Boolean deleted,
            JsonNode scenes,
            String errorMessage,
            String text) {
    }
}
