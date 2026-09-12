package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record AiChatConversationMessagesVO(
        String conversationId,
        List<AiChatConversationMessageVO> messages,
        boolean hasMore,
        String nextBeforeId) {
}
