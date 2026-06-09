package com.scrapider.finance.ai.domain.dto;

public record AiChatConversationBindingDTO(
        Long userId,
        String conversationId,
        Long cleanupVersion) {
}
