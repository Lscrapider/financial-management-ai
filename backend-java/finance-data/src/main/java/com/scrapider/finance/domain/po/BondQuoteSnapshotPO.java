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
@TableName("bond_quote_snapshot")
public class BondQuoteSnapshotPO {

    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_\\w+=\"(.*)\";");

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
    private BigDecimal conversionPremiumRate;
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
        snapshot.setConversionPremiumRate(extractConversionPremiumRate(fields));
        snapshot.setRawResponse(response);
        snapshot.setSyncedAt(now);
        return snapshot;
    }

    private static BigDecimal extractConversionPremiumRate(String[] fields) {
        for (int i = fields.length - 1; i >= 0; i--) {
            if ("ZQ-KZZ".equals(fields[i].trim()) && i + 13 < fields.length) {
                return StockMarketJsonParser.decimal(fields[i + 13]);
            }
        }
        return null;
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
