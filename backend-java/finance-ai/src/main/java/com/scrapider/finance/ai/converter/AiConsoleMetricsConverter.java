package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.AiConsoleOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AppUserOverviewVO;
import com.scrapider.finance.ai.domain.vo.AppVisitOverviewVO;
import com.scrapider.finance.domain.dto.AppVisitSummaryDTO;

public final class AiConsoleMetricsConverter {

    private AiConsoleMetricsConverter() {
    }

    public static AiConsoleOverviewVO overview(
            Long enabledUserCount,
            AppVisitSummaryDTO visitSummary,
            AiTokenUsageOverviewVO tokenUsage) {
        return new AiConsoleOverviewVO(
                new AppUserOverviewVO(enabledUserCount),
                AppVisitOverviewVO.fromDTO(visitSummary),
                tokenUsage);
    }
}
