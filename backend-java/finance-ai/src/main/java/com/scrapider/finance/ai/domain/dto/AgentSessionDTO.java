package com.scrapider.finance.ai.domain.dto;

import java.time.Instant;
import java.util.Set;

public record AgentSessionDTO(
        String agentSessionId,
        String sessionSecret,
        Long userId,
        String username,
        String conversationId,
        String messageId,
        Set<String> scopes,
        Instant expiresAt) {

    public boolean expired(Instant now) {
        return !this.expiresAt.isAfter(now);
    }
}
