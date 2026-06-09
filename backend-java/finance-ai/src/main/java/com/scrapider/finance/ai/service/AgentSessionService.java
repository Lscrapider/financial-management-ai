package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import java.util.Optional;

public interface AgentSessionService {

    AgentSessionDTO create(Long userId, String username, String conversationId, String messageId);

    Optional<AgentSessionDTO> findActive(String agentSessionId);

    boolean markNonceUsed(String agentSessionId, String nonce);
}
