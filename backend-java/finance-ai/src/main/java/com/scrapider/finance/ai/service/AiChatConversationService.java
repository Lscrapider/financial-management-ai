package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.AiChatConversationBindingDTO;
import com.scrapider.finance.ai.domain.dto.ConversationCleanupMessageDTO;
import java.util.List;
import java.util.Map;

public interface AiChatConversationService {

    AiChatConversationBindingDTO bind(Long userId);

    AiChatConversationBindingDTO bind(Long userId, String conversationId);

    void release(Long userId, String conversationId);

    void saveUserMessage(Long userId, String conversationId, String messageId, String content);

    void saveAssistantMessage(Long userId, String conversationId, String messageId, String content);

    List<Map<String, Object>> listHistory(Long userId, String conversationId, String excludeMessageId, int limit);

    void cleanup(ConversationCleanupMessageDTO message);
}
