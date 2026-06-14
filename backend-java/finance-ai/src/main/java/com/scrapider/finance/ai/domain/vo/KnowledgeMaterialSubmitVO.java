package com.scrapider.finance.ai.domain.vo;

public record KnowledgeMaterialSubmitVO(
        String taskNo,
        String searchMode,
        String targetType,
        String targetCode,
        String targetName,
        String queryText,
        String rewrittenQuery,
        String status) {
}
