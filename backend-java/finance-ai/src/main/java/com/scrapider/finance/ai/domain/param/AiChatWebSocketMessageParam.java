package com.scrapider.finance.ai.domain.param;

public record AiChatWebSocketMessageParam(
        String type,
        String conversationId,
        String messageId,
        String content) {
}
