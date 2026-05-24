package com.scrapider.finance.ai.domain.vo;

import java.util.List;
import java.util.Map;

public record AiDatabaseContextVO(List<Map<String, Object>> results) {

    public static AiDatabaseContextVO empty() {
        return new AiDatabaseContextVO(List.of());
    }
}
