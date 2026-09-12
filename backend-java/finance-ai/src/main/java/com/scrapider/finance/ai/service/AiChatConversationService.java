package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.AiChatConversationBindingDTO;
import com.scrapider.finance.ai.domain.dto.ConversationCleanupMessageDTO;
import com.scrapider.finance.ai.domain.vo.AiChatConversationMessagesVO;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiChatConversationService {

    AiChatConversationBindingDTO bind(Long userId);

    AiChatConversationBindingDTO bind(Long userId, String conversationId);

    void release(Long userId, String conversationId);

    void saveUserMessage(Long userId, String conversationId, String messageId, String content);

    void saveAssistantMessage(Long userId, String conversationId, String messageId, String content);

    List<Map<String, Object>> listHistory(Long userId, String conversationId, String excludeMessageId, int limit);

    Optional<AiChatConversationMessagesVO> listMessages(Long userId, String conversationId, Long beforeId);

    void cleanup(ConversationCleanupMessageDTO message);
}
