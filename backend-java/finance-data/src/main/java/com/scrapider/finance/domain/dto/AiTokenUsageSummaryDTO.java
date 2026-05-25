package com.scrapider.finance.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiTokenUsageSummaryDTO {

    private Long requestCount;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long cachedTokens;
    private Long reasoningTokens;
    private LocalDateTime latestOccurredAt;
}
