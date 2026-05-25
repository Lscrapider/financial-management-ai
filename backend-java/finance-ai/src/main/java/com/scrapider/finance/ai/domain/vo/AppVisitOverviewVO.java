package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.dto.AppVisitSummaryDTO;
import java.time.LocalDateTime;

public record AppVisitOverviewVO(
        Long totalVisitCount,
        Long periodVisitCount,
        Long uniqueUserCount,
        LocalDateTime latestOccurredAt) {

    public static AppVisitOverviewVO fromDTO(AppVisitSummaryDTO dto) {
        if (dto == null) {
            return new AppVisitOverviewVO(0L, 0L, 0L, null);
        }
        return new AppVisitOverviewVO(
                value(dto.getTotalVisitCount()),
                value(dto.getPeriodVisitCount()),
                value(dto.getUniqueUserCount()),
                dto.getLatestOccurredAt());
    }

    private static Long value(Long value) {
        return value == null ? 0L : value;
    }
}
