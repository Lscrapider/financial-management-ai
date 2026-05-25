package com.scrapider.finance.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AppVisitTrendDTO {

    private LocalDateTime timeBucket;
    private Long visitCount;
    private Long uniqueUserCount;
}
