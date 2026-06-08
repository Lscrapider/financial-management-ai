package com.scrapider.finance.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentCallbackParam(
        String agentSessionId,
        String conversationId,
        String messageId,
        String eventType,
        JsonNode payload) {
}
