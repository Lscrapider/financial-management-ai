package com.scrapider.finance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class StockMarketApi {

    private static final String SOURCE = "eastmoney";
    private static final String STOCK_QUOTE_URL =
            "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String STOCK_TRENDS_URL =
            "https://push2his.eastmoney.com/api/qt/stock/trends2/get";
    private static final String QUOTE_FIELDS =
            "f43,f44,f45,f46,f47,f48,f50,f51,f52,f57,f58,f60,f116,f117,"
                    + "f162,f167,f168,f169,f170,f171,f292";
    private static final String TRENDS_FIELDS1 =
            "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13";
    private static final String TRENDS_FIELDS2 =
            "f51,f52,f53,f54,f55,f56,f57,f58";
    private final RestTemplate restTemplate;

    public StockMarketApi(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public StockMarketDataDTO getQuote(String secid) {
        String url = UriComponentsBuilder.fromUriString(STOCK_QUOTE_URL)
                .queryParam("secid", secid)
                .queryParam("fields", QUOTE_FIELDS)
                .toUriString();
        return this.get(url);
    }

    public StockMarketDataDTO getTrends(String secid, int ndays) {
        String url = UriComponentsBuilder.fromUriString(STOCK_TRENDS_URL)
                .queryParam("secid", secid)
                .queryParam("fields1", TRENDS_FIELDS1)
                .queryParam("fields2", TRENDS_FIELDS2)
                .queryParam("iscr", 0)
                .queryParam("iscca", 0)
                .queryParam("ndays", ndays)
                .toUriString();
        return this.get(url);
    }

    private StockMarketDataDTO get(String url) {
        JsonNode body = this.restTemplate.getForObject(url, JsonNode.class);
        return new StockMarketDataDTO(SOURCE, url, body);
    }
}
