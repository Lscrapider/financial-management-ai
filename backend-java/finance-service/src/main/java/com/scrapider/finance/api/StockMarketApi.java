package com.scrapider.finance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public StockMarketApi(@Qualifier("marketRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
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

    public StockMarketDataDTO getQuotes(List<String> secids) {
        String symbolParam = secids.stream()
                .map(this::toTencentSymbol)
                .collect(Collectors.joining(","));
        String url = TENCENT_QUOTE_URL.replace("{symbol}", symbolParam);
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
        return this.getKlines(secid, KlinePeriodTypeEnum.DAILY, KlineAdjustTypeEnum.HFQ, limit);
    }

    public StockMarketDataDTO getKlines(
            String secid,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Integer limit) {
        String symbol = this.toTencentSymbol(secid);
        KlinePeriodTypeEnum normalizedPeriod = periodType == null ? KlinePeriodTypeEnum.DAILY : periodType;
        KlineAdjustTypeEnum normalizedAdjust = adjustType == null ? KlineAdjustTypeEnum.HFQ : adjustType;
        String param = "%s,%s,,,%d,%s".formatted(
                symbol,
                normalizedPeriod.getTencentCode(),
                limit == null || limit < 1 ? 250 : limit,
                normalizedAdjust.getCode());
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
