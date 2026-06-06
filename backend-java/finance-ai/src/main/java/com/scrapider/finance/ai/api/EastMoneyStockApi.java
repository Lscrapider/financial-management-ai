package com.scrapider.finance.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EastMoneyStockApi {

    private static final String SOURCE = "eastmoney";
    private static final String STOCK_GET_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String STOCK_INFO_FIELDS = "f43,f57,f58,f116,f117,f127,f128,f129,f162,f167,f168,f170";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyStockApi(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public StockMarketDataDTO getStockInfo(String secid) {
        String url = STOCK_GET_URL
                + "?secid=" + secid
                + "&fields=" + STOCK_INFO_FIELDS;
        return this.get(url);
    }

    private StockMarketDataDTO get(String url) {
        String body = this.restTemplate.getForObject(url, String.class);
        return new StockMarketDataDTO(SOURCE, url, this.jsonNode(body));
    }

    private JsonNode jsonNode(String body) {
        try {
            return this.objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid eastmoney api json response", ex);
        }
    }
}
