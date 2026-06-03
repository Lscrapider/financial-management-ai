package com.scrapider.finance.ai.domain.vo;

public record SceneAnalysisTargetOptionVO(
        String targetType,
        String targetCode,
        String targetName,
        String secid,
        String marketCode,
        String exchangeCode) {
}
