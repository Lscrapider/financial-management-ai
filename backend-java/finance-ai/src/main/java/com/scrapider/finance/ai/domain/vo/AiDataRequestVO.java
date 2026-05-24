package com.scrapider.finance.ai.domain.vo;

public record AiDataRequestVO(
        String source,
        String queryType,
        String targetCode,
        String targetName,
        Integer limit) {
}
