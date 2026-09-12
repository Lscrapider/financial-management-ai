package com.scrapider.finance.ai.domain.vo;

public record AiChatConversationMessageVO(
        String id,
        String messageId,
        String role,
        String content,
        String createdAt) {
}
