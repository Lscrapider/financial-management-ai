package com.scrapider.finance.ai.domain.dto;

public record SceneAnalysisTargetDTO(
        String type,
        String code,
        String name,
        String secid,
        String marketCode,
        String exchangeCode) {
}
