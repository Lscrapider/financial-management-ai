package com.scrapider.finance.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record StockMarketDataDTO(String source, String requestUrl, JsonNode data) {
}
