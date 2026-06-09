package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentCallbackParam(
        String agentSessionId,
        String conversationId,
        String messageId,
        String eventType,
        JsonNode payload) {
}
