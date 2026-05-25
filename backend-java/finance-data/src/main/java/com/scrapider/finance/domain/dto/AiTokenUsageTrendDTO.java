package com.scrapider.finance.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiTokenUsageTrendDTO {

    private LocalDateTime timeBucket;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long requestCount;
}
