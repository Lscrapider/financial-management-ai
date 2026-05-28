package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record AiQueryRewriteVO(
        Boolean enabled,
        String disabledReason,
        String intent,
        Boolean requiresMarketData,
        String targetType,
        String targetName,
        String stockCode,
        String indexCode,
        String bondCode,
        String timeRange,
        List<String> dataScopes,
        List<AiDataRequestVO> dataRequests,
        String rewrittenQuestion) {

    public static AiQueryRewriteVO fallback(String message) {
        return new AiQueryRewriteVO(
                true,
                "",
                "general_chat",
                false,
                "none",
                "",
                "",
                "",
                "",
                "none",
                List.of(),
                List.of(),
                message);
    }
}
