package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

@Data
@TableName("stock_quote_snapshot")
public class StockQuoteSnapshotPO {

    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_(\\w+)=\"(.*)\";");

    private Long id;
    private String stockCode;
    private String stockName;
    private String secid;
    private String marketCode;
    private String exchangeCode;
    private BigDecimal latestPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClosePrice;
    private BigDecimal averagePrice;
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private Long externalVolume;
    private Long internalVolume;
    private Long currentVolume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal volumeRatio;
    private BigDecimal limitUpPrice;
    private BigDecimal limitDownPrice;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private BigDecimal peTtm;
    private BigDecimal peDynamic;
    private BigDecimal peStatic;
    private BigDecimal pbRatio;
    private Integer tradeStatus;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockQuoteSnapshotPO fromApiResponse(StockConfigPO stock, JsonNode response) {
        return fromTencentQuote(stock, response.asText());
    }

    private static StockQuoteSnapshotPO fromTencentQuote(StockConfigPO stock, String response) {
        String[] fields = extractTencentFields(response);
        return populateFromFields(stock, fields, response);
    }

    private static StockQuoteSnapshotPO populateFromFields(StockConfigPO stock, String[] fields, String rawResponse) {
        LocalDateTime now = LocalDateTime.now();

        StockQuoteSnapshotPO snapshot = new StockQuoteSnapshotPO();
        snapshot.setStockCode(value(fields, 2, stock.getStockCode()));
        snapshot.setStockName(value(fields, 1, stock.getStockName()));
        snapshot.setSecid(stock.getSecid());
        snapshot.setMarketCode(stock.getMarketCode());
        snapshot.setExchangeCode(stock.getExchangeCode());
        snapshot.setLatestPrice(StockMarketJsonParser.decimal(value(fields, 3, null)));
        snapshot.setPreviousClosePrice(StockMarketJsonParser.decimal(value(fields, 4, null)));
        snapshot.setOpenPrice(StockMarketJsonParser.decimal(value(fields, 5, null)));
        snapshot.setVolume(StockMarketJsonParser.longValue(value(fields, 6, null)));
        snapshot.setExternalVolume(StockMarketJsonParser.longValue(value(fields, 7, null)));
        snapshot.setInternalVolume(StockMarketJsonParser.longValue(value(fields, 8, null)));
        snapshot.setChangeAmount(StockMarketJsonParser.decimal(value(fields, 31, null)));
        snapshot.setChangePercent(StockMarketJsonParser.decimal(value(fields, 32, null)));
        snapshot.setHighPrice(StockMarketJsonParser.decimal(value(fields, 33, null)));
        snapshot.setLowPrice(StockMarketJsonParser.decimal(value(fields, 34, null)));
        snapshot.setTurnoverAmount(StockMarketJsonParser.decimal(value(fields, 37, null)));
        snapshot.setTurnoverRate(StockMarketJsonParser.decimal(value(fields, 38, null)));
        snapshot.setPeTtm(StockMarketJsonParser.decimal(value(fields, 39, null)));
        snapshot.setAmplitude(StockMarketJsonParser.decimal(value(fields, 43, null)));
        snapshot.setFloatMarketValue(StockMarketJsonParser.decimal(value(fields, 44, null)));
        snapshot.setTotalMarketValue(StockMarketJsonParser.decimal(value(fields, 45, null)));
        snapshot.setPbRatio(StockMarketJsonParser.decimal(value(fields, 46, null)));
        snapshot.setLimitUpPrice(StockMarketJsonParser.decimal(value(fields, 47, null)));
        snapshot.setLimitDownPrice(StockMarketJsonParser.decimal(value(fields, 48, null)));
        snapshot.setVolumeRatio(StockMarketJsonParser.decimal(value(fields, 49, null)));
        snapshot.setCurrentVolume(StockMarketJsonParser.longValue(value(fields, 50, null)));
        snapshot.setAveragePrice(StockMarketJsonParser.decimal(value(fields, 51, null)));
        snapshot.setPeDynamic(StockMarketJsonParser.decimal(value(fields, 52, null)));
        snapshot.setPeStatic(StockMarketJsonParser.decimal(value(fields, 53, null)));
        snapshot.setTradeStatus(StockMarketJsonParser.intValue(value(fields, 59, null)));
        snapshot.setRawResponse(rawResponse);
        snapshot.setSyncedAt(now);
        snapshot.roundDecimals();
        return snapshot;
    }

    public static String[] extractTencentFields(String response) {
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

    public static List<StockQuoteSnapshotPO> fromBatchApiResponse(
            String response, Map<String, StockConfigPO> symbolToStock) {
        List<StockQuoteSnapshotPO> results = new ArrayList<>();
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
            StockConfigPO stock = symbolToStock.get(symbol);
            if (stock == null) {
                continue;
            }
            String[] fields = extractTencentFields(line);
            if (fields.length == 0) {
                continue;
            }
            results.add(populateFromFields(stock, fields, line));
        }
        return results;
    }

    private static String value(String[] fields, int index, String defaultValue) {
        if (index < 0 || index >= fields.length) {
            return defaultValue;
        }
        return fields[index];
    }

    private void roundDecimals() {
        this.latestPrice = decimal3(this.latestPrice);
        this.openPrice = decimal3(this.openPrice);
        this.highPrice = decimal3(this.highPrice);
        this.lowPrice = decimal3(this.lowPrice);
        this.previousClosePrice = decimal3(this.previousClosePrice);
        this.averagePrice = decimal3(this.averagePrice);
        this.changeAmount = decimal3(this.changeAmount);
        this.changePercent = decimal3(this.changePercent);
        this.turnoverAmount = decimal3(this.turnoverAmount);
        this.turnoverRate = decimal3(this.turnoverRate);
        this.amplitude = decimal3(this.amplitude);
        this.volumeRatio = decimal3(this.volumeRatio);
        this.limitUpPrice = decimal3(this.limitUpPrice);
        this.limitDownPrice = decimal3(this.limitDownPrice);
        this.totalMarketValue = decimal3(this.totalMarketValue);
        this.floatMarketValue = decimal3(this.floatMarketValue);
        this.peTtm = decimal3(this.peTtm);
        this.peDynamic = decimal3(this.peDynamic);
        this.peStatic = decimal3(this.peStatic);
        this.pbRatio = decimal3(this.pbRatio);
    }

    private static BigDecimal decimal3(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }
}
