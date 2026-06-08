package com.scrapider.finance.domain.param;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentDataQueryParam(
        String action,
        JsonNode params,
        Integer limit) {
}
