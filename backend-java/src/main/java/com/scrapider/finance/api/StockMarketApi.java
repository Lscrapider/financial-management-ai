package com.scrapider.finance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import java.io.IOException;
import java.nio.charset.Charset;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class StockMarketApi {

    private static final String SOURCE = "tencent";
    private static final Charset GBK = Charset.forName("GBK");
    private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q={symbol}";
    private static final String TENCENT_TRENDS_URL =
            "https://web.ifzq.gtimg.cn/appstock/app/minute/query";
    private static final String TENCENT_DAILY_KLINE_URL =
            "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public StockMarketApi(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public StockMarketDataDTO getQuote(String secid) {
        String symbol = this.toTencentSymbol(secid);
        String url = TENCENT_QUOTE_URL.replace("{symbol}", symbol);
        byte[] bytes = this.restTemplate.getForObject(url, byte[].class);
        String body = bytes == null ? "" : new String(bytes, GBK);
        return new StockMarketDataDTO(SOURCE, url, this.textNode(body));
    }

    public StockMarketDataDTO getTrends(String secid) {
        String symbol = this.toTencentSymbol(secid);
        String url = UriComponentsBuilder.fromUriString(TENCENT_TRENDS_URL)
                .queryParam("code", symbol)
                .toUriString();
        return this.get(url);
    }

    public StockMarketDataDTO getDailyKlines(String secid, Integer limit) {
        String symbol = this.toTencentSymbol(secid);
        String param = "%s,day,,,%d,qfq".formatted(symbol, limit == null || limit < 1 ? 250 : limit);
        String url = UriComponentsBuilder.fromUriString(TENCENT_DAILY_KLINE_URL)
                .queryParam("param", param)
                .toUriString();
        return this.get(url);
    }

    private StockMarketDataDTO get(String url) {
        String body = this.restTemplate.getForObject(url, String.class);
        return new StockMarketDataDTO(SOURCE, url, this.jsonNode(body));
    }

    private JsonNode textNode(String body) {
        return this.objectMapper.getNodeFactory().textNode(body);
    }

    private JsonNode jsonNode(String body) {
        try {
            return this.objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid tencent api json response", ex);
        }
    }

    private String toTencentSymbol(String secid) {
        String[] parts = secid.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + secid);
        }
        return "1".equals(parts[0]) ? "sh" + parts[1] : "sz" + parts[1];
    }
}
