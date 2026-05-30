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
@TableName("bond_quote_snapshot")
public class BondQuoteSnapshotPO {

    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_(\\w+)=\"(.*)\";");

    private Long id;
    private String bondCode;
    private String bondName;
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
    private BigDecimal averagePrice;
    private Long currentVolume;
    private String bondRating;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BondQuoteSnapshotPO fromApiResponse(BondConfigPO bond, JsonNode response) {
        return fromTencentQuote(bond, response.asText());
    }

    private static BondQuoteSnapshotPO fromTencentQuote(BondConfigPO bond, String response) {
        String[] fields = extractTencentFields(response);
        return populateFromFields(bond, fields, response);
    }

    private static BondQuoteSnapshotPO populateFromFields(BondConfigPO bond, String[] fields, String rawResponse) {
        LocalDateTime now = LocalDateTime.now();

        BondQuoteSnapshotPO snapshot = new BondQuoteSnapshotPO();
        snapshot.setBondCode(value(fields, 2, bond.getBondCode()));
        snapshot.setBondName(value(fields, 1, bond.getBondName()));
        snapshot.setSecid(bond.getSecid());
        snapshot.setMarketCode(bond.getMarketCode());
        snapshot.setExchangeCode(bond.getExchangeCode());
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
        snapshot.setAmplitude(StockMarketJsonParser.decimal(value(fields, 43, null)));
        snapshot.setAveragePrice(StockMarketJsonParser.decimal(value(fields, 51, null)));
        snapshot.setCurrentVolume(StockMarketJsonParser.longValue(value(fields, 50, null)));
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

    public static List<BondQuoteSnapshotPO> fromBatchApiResponse(
            String response, Map<String, BondConfigPO> symbolToBond) {
        List<BondQuoteSnapshotPO> results = new ArrayList<>();
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
            BondConfigPO bond = symbolToBond.get(symbol);
            if (bond == null) {
                continue;
            }
            String[] fields = extractTencentFields(line);
            if (fields.length == 0) {
                continue;
            }
            results.add(populateFromFields(bond, fields, line));
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
