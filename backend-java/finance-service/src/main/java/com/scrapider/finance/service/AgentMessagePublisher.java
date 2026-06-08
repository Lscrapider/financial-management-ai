package com.scrapider.finance.service;

import com.scrapider.finance.domain.dto.AgentRunStartMessageDTO;
import com.scrapider.finance.domain.dto.ConversationCleanupMessageDTO;

public interface AgentMessagePublisher {

    void publishRunStart(AgentRunStartMessageDTO message);

    void publishConversationCleanup(ConversationCleanupMessageDTO message);
}
