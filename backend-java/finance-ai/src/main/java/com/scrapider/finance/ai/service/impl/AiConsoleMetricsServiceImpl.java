package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.converter.AiConsoleMetricsConverter;
import com.scrapider.finance.ai.domain.vo.AiConsoleOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AppVisitTrendVO;
import com.scrapider.finance.ai.service.AiConsoleMetricsService;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import com.scrapider.finance.manage.AppUserManage;
import com.scrapider.finance.manage.AppVisitLogManage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiConsoleMetricsServiceImpl implements AiConsoleMetricsService {

    private static final int DEFAULT_OVERVIEW_DAYS = 7;
    private static final int MAX_OVERVIEW_DAYS = 365;
    private static final int DEFAULT_TREND_HOURS = 24;
    private static final int MAX_TREND_HOURS = 24 * 30;

    private final AppUserManage appUserManage;
    private final AppVisitLogManage appVisitLogManage;
    private final AiTokenUsageService aiTokenUsageService;

    public AiConsoleMetricsServiceImpl(
            AppUserManage appUserManage,
            AppVisitLogManage appVisitLogManage,
            AiTokenUsageService aiTokenUsageService) {
        this.appUserManage = appUserManage;
        this.appVisitLogManage = appVisitLogManage;
        this.aiTokenUsageService = aiTokenUsageService;
    }

    @Override
    public AiConsoleOverviewVO overview(Integer days) {
        int normalizedDays = this.normalize(days, DEFAULT_OVERVIEW_DAYS, MAX_OVERVIEW_DAYS);
        LocalDateTime startTime = LocalDateTime.now().minusDays(normalizedDays);
        AiTokenUsageOverviewVO tokenUsage = this.aiTokenUsageService.overview(normalizedDays);
        return AiConsoleMetricsConverter.overview(
                this.appUserManage.countEnabledUsers(),
                this.appVisitLogManage.summarySince(startTime),
                tokenUsage);
    }

    @Override
    public List<AppVisitTrendVO> visitTrends(Integer hours) {
        LocalDateTime startTime = LocalDateTime.now()
                .minusHours(this.normalize(hours, DEFAULT_TREND_HOURS, MAX_TREND_HOURS));
        return this.appVisitLogManage.trendSince(startTime).stream()
                .map(AppVisitTrendVO::fromDTO)
                .toList();
    }

    private int normalize(Integer value, int defaultValue, int maxValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }
}
