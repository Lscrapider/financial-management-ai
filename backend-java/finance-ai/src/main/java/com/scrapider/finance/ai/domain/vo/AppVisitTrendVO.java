package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.dto.AppVisitTrendDTO;
import java.time.LocalDateTime;

public record AppVisitTrendVO(LocalDateTime timeBucket, Long visitCount, Long uniqueUserCount) {

    public static AppVisitTrendVO fromDTO(AppVisitTrendDTO dto) {
        return new AppVisitTrendVO(
                dto.getTimeBucket(),
                value(dto.getVisitCount()),
                value(dto.getUniqueUserCount()));
    }

    private static Long value(Long value) {
        return value == null ? 0L : value;
    }
}
