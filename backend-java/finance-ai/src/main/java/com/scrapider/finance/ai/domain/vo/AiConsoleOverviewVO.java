package com.scrapider.finance.ai.domain.vo;

public record AiConsoleOverviewVO(
        AppUserOverviewVO user,
        AppVisitOverviewVO visit,
        AiTokenUsageOverviewVO tokenUsage) {
}
