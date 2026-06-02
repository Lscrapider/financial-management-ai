package com.scrapider.finance.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EastMoneyFinanceApi {

    private static final String SOURCE = "eastmoney";
    private static final String DATA_CENTER_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyFinanceApi(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public StockMarketDataDTO getMainFinancialIndicators(String secucode, int pageSize) {
        String url = DATA_CENTER_URL
                + "?reportName=RPT_F10_FINANCE_MAINFINADATA"
                + "&columns=ALL"
                + "&filter=(SECUCODE=\"" + secucode + "\")"
                + "&pageNumber=1"
                + "&pageSize=" + Math.max(pageSize, 1)
                + "&sortColumns=REPORT_DATE"
                + "&sortTypes=-1";
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
