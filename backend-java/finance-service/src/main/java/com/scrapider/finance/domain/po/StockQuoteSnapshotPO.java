package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

@Data
@TableName("stock_quote_snapshot")
public class StockQuoteSnapshotPO {

    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_\\w+=\"(.*)\";");

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
    private BigDecimal changeAmount;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal turnoverAmount;
    private BigDecimal turnoverRate;
    private BigDecimal amplitude;
    private BigDecimal volumeRatio;
    private BigDecimal limitUpPrice;
    private BigDecimal limitDownPrice;
    private BigDecimal totalMarketValue;
    private BigDecimal floatMarketValue;
    private BigDecimal peTtm;
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
        snapshot.setChangeAmount(StockMarketJsonParser.decimal(value(fields, 31, null)));
        snapshot.setChangePercent(StockMarketJsonParser.decimal(value(fields, 32, null)));
        snapshot.setHighPrice(StockMarketJsonParser.decimal(value(fields, 33, null)));
        snapshot.setLowPrice(StockMarketJsonParser.decimal(value(fields, 34, null)));
        snapshot.setTurnoverAmount(StockMarketJsonParser.decimal(value(fields, 37, null)));
        snapshot.setTurnoverRate(StockMarketJsonParser.decimal(value(fields, 38, null)));
        snapshot.setPeTtm(StockMarketJsonParser.decimal(value(fields, 39, null)));
        snapshot.setAmplitude(StockMarketJsonParser.decimal(value(fields, 43, null)));
        snapshot.setTotalMarketValue(StockMarketJsonParser.decimal(value(fields, 44, null)));
        snapshot.setFloatMarketValue(StockMarketJsonParser.decimal(value(fields, 45, null)));
        snapshot.setPbRatio(StockMarketJsonParser.decimal(value(fields, 46, null)));
        snapshot.setLimitUpPrice(StockMarketJsonParser.decimal(value(fields, 47, null)));
        snapshot.setLimitDownPrice(StockMarketJsonParser.decimal(value(fields, 48, null)));
        snapshot.setVolumeRatio(StockMarketJsonParser.decimal(value(fields, 49, null)));
        snapshot.setRawResponse(response);
        snapshot.setSyncedAt(now);
        return snapshot;
    }

    private static String[] extractTencentFields(String response) {
        Matcher matcher = TENCENT_QUOTE_PATTERN.matcher(response);
        if (!matcher.find()) {
            return new String[0];
        }
        return matcher.group(1).split("~", -1);
    }

    private static String value(String[] fields, int index, String defaultValue) {
        if (index < 0 || index >= fields.length) {
            return defaultValue;
        }
        return fields[index];
    }
}
