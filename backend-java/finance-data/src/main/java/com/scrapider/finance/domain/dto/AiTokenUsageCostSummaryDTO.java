package com.scrapider.finance.domain.dto;

import lombok.Data;

@Data
public class AiTokenUsageCostSummaryDTO {

    private String model;
    private Long promptTokens;
    private Long completionTokens;
    private Long cachedTokens;
    private Long promptCacheHitTokens;
    private Long promptCacheMissTokens;
}
