package com.scrapider.finance.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AppVisitSummaryDTO {

    private Long totalVisitCount;
    private Long periodVisitCount;
    private Long uniqueUserCount;
    private LocalDateTime latestOccurredAt;
}
