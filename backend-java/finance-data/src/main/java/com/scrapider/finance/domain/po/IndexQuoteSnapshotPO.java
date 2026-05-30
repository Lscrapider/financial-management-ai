package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

@Data
@TableName("index_quote_snapshot")
public class IndexQuoteSnapshotPO {

    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_(\\w+)=\"(.*)\";");

    private Long id;
    private String indexCode;
    private String indexName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal amplitude;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static IndexQuoteSnapshotPO fromApiResponse(IndexConfigPO indexConfig, JsonNode response) {
        return fromTencentQuote(indexConfig, response.asText());
    }

    private static IndexQuoteSnapshotPO fromTencentQuote(IndexConfigPO indexConfig, String response) {
        String[] fields = extractTencentFields(response);
        return populateFromFields(indexConfig, fields, response);
    }

    private static IndexQuoteSnapshotPO populateFromFields(IndexConfigPO indexConfig, String[] fields, String rawResponse) {
        LocalDateTime now = LocalDateTime.now();

        IndexQuoteSnapshotPO snapshot = new IndexQuoteSnapshotPO();
        snapshot.setIndexCode(value(fields, 2, indexConfig.getIndexCode()));
        snapshot.setIndexName(value(fields, 1, indexConfig.getIndexName()));
        snapshot.setSecid(indexConfig.getSecid());
        snapshot.setMarketCode(indexConfig.getMarketCode());
        snapshot.setExchangeCode(indexConfig.getExchangeCode());
        snapshot.setLatestPrice(StockMarketJsonParser.decimal(value(fields, 3, null)));
        snapshot.setPreviousClosePrice(StockMarketJsonParser.decimal(value(fields, 4, null)));
        snapshot.setOpenPrice(StockMarketJsonParser.decimal(value(fields, 5, null)));
        snapshot.setVolume(StockMarketJsonParser.longValue(value(fields, 6, null)));
        snapshot.setChangeAmount(StockMarketJsonParser.decimal(value(fields, 31, null)));
        snapshot.setChangePercent(StockMarketJsonParser.decimal(value(fields, 32, null)));
        snapshot.setHighPrice(StockMarketJsonParser.decimal(value(fields, 33, null)));
        snapshot.setLowPrice(StockMarketJsonParser.decimal(value(fields, 34, null)));
        snapshot.setTurnoverAmount(StockMarketJsonParser.decimal(value(fields, 37, null)));
        snapshot.setAmplitude(StockMarketJsonParser.decimal(value(fields, 43, null)));
        snapshot.setRawResponse(rawResponse);
        snapshot.setSyncedAt(now);
        return snapshot;
    }

    private static String[] extractTencentFields(String response) {
        if (response == null) {
            return new String[0];
        }
        Matcher matcher = TENCENT_QUOTE_PATTERN.matcher(response);
        if (!matcher.find()) {
            return new String[0];
        }
        return matcher.group(2).split("~", -1);
    }

    public static String extractTencentSymbol(String response) {
        if (response == null) {
            return null;
        }
        Matcher matcher = TENCENT_QUOTE_PATTERN.matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static List<IndexQuoteSnapshotPO> fromBatchApiResponse(
            String response, Map<String, IndexConfigPO> symbolToIndex) {
        List<IndexQuoteSnapshotPO> results = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return results;
        }
        for (String line : response.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String symbol = extractTencentSymbol(line);
            if (symbol == null) {
                continue;
            }
            IndexConfigPO index = symbolToIndex.get(symbol);
            if (index == null) {
                continue;
            }
            String[] fields = extractTencentFields(line);
            if (fields.length == 0) {
                continue;
            }
            results.add(populateFromFields(index, fields, line));
        }
        return results;
    }

    private static String value(String[] fields, int index, String defaultValue) {
        if (index < 0 || index >= fields.length) {
            return defaultValue;
        }
        return fields[index];
    }
}
