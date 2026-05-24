package com.scrapider.finance.ai.domain.vo;

import java.time.LocalDateTime;

public record AiChatVO(
        String message,
        String answer,
        String model,
        AiQueryRewriteVO queryRewrite,
        AiDatabaseContextVO databaseContext,
        LocalDateTime answeredAt) {
}
