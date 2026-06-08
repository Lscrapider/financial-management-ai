package com.scrapider.finance.service;

import com.scrapider.finance.domain.dto.AgentSessionDTO;
import java.util.Optional;

public interface AgentSessionService {

    AgentSessionDTO create(Long userId, String username, String conversationId, String messageId);

    Optional<AgentSessionDTO> findActive(String agentSessionId);

    boolean markNonceUsed(String agentSessionId, String nonce);
}
