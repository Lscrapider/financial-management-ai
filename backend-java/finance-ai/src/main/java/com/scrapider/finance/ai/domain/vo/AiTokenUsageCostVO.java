package com.scrapider.finance.ai.domain.vo;

import java.math.BigDecimal;

public record AiTokenUsageCostVO(
        BigDecimal cacheHitInputCost,
        BigDecimal cacheMissInputCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        String currency) {
}
