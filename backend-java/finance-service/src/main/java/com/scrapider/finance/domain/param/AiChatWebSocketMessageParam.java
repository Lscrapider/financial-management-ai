package com.scrapider.finance.domain.param;

public record AiChatWebSocketMessageParam(
        String type,
        String conversationId,
        String messageId,
        String content) {
}
