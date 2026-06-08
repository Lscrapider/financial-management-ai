package com.scrapider.finance.domain.dto;

public record AiChatConversationBindingDTO(
        Long userId,
        String conversationId,
        Long cleanupVersion) {
}
