package com.scrapider.finance.ai.domain.vo;

public record SceneAnalysisSubmitVO(
        String taskNo,
        String targetType,
        String targetCode,
        String configProfile,
        String status) {
}
