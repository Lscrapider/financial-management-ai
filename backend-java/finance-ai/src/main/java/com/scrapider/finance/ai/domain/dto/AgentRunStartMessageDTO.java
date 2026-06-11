package com.scrapider.finance.ai.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;

public record AgentRunStartMessageDTO(
        String type,
        String agentSessionId,
        String conversationId,
        String messageId,
        Long userId,
        String username,
        String userMessage,
        String sessionSecret,
        Set<String> scopes,
        Instant expiresAt,
        String dataGatewayUrl,
        String callbackUrl,
        JsonNode executionBudget) {

    public static AgentRunStartMessageDTO from(
            AgentSessionDTO session,
            String userMessage,
            String dataGatewayUrl,
            String callbackUrl,
            JsonNode executionBudget) {
        return new AgentRunStartMessageDTO(
                "agent.run.start",
                session.agentSessionId(),
                session.conversationId(),
                session.messageId(),
                session.userId(),
                session.username(),
                userMessage,
                session.sessionSecret(),
                session.scopes(),
                session.expiresAt(),
                dataGatewayUrl,
                callbackUrl,
                executionBudget);
    }
}
