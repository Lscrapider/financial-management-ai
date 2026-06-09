package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentDataGatewayResponseVO(
        String action,
        boolean success,
        List<Map<String, Object>> data,
        Map<String, Object> metadata,
        Error error) {

    public static AgentDataGatewayResponseVO empty(String action) {
        return new AgentDataGatewayResponseVO(
                action,
                true,
                List.of(),
                Map.of("queriedAt", OffsetDateTime.now().toString()),
                null);
    }

    public record Error(String code, String message) {
    }
}
