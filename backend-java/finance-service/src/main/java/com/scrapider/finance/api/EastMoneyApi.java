package com.scrapider.finance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EastMoneyApi {

    private static final String SOURCE = "eastmoney";
    private static final String DATA_CENTER_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final String STOCK_GET_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String STOCK_INFO_FIELDS = "f43,f57,f58,f116,f117,f127,f128,f129,f162,f167,f168,f170";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyApi(@Qualifier("marketRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public StockMarketDataDTO getDividendHistory(String stockCode, int pageSize) {
        String url = DATA_CENTER_URL
                + "?reportName=RPT_SHAREBONUS_DET"
                + "&columns=ALL"
                + "&filter=(SECURITY_CODE=" + stockCode + ")"
                + "&pageNumber=1"
                + "&pageSize=" + Math.max(pageSize, 1)
                + "&sortColumns=EX_DIVIDEND_DATE"
                + "&sortTypes=-1";
        return this.get(url);
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

    public StockMarketDataDTO getStockInfo(String secid) {
        String url = STOCK_GET_URL
                + "?secid=" + secid
                + "&fields=" + STOCK_INFO_FIELDS;
        return this.get(url);
    }

    public StockMarketDataDTO getValuationHistory(String stockCode, int pageSize) {
        String url = DATA_CENTER_URL
                + "?reportName=RPT_VALUEANALYSIS_DET"
                + "&columns=ALL"
                + "&filter=(SECURITY_CODE=" + stockCode + ")"
                + "&pageNumber=1"
                + "&pageSize=" + Math.max(pageSize, 1)
                + "&sortColumns=TRADE_DATE"
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
