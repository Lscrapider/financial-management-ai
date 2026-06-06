package com.scrapider.finance.domain.po;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("convertible_bond_daily_valuation")
public class ConvertibleBondDailyValuationPO {

    private Long id;
    private String bondCode;
    private String bondName;
    private String tsCode;
    private LocalDate tradeDate;
    private BigDecimal closePrice;
    private BigDecimal conversionValue;
    private BigDecimal premiumRate;
    private BigDecimal pureBondValue;
    private BigDecimal pureBondPremiumRate;
    private BigDecimal ytm;
    private Long volume;
    private BigDecimal turnoverAmount;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<ConvertibleBondDailyValuationPO> fromTushareRows(BondConfigPO bond, JsonNode rows) {
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(bond, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static ConvertibleBondDailyValuationPO fromTushareRow(
            BondConfigPO bond,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate tradeDate = date(row, "trade_date");
        if (tradeDate == null) {
            return null;
        }
        ConvertibleBondDailyValuationPO valuation = new ConvertibleBondDailyValuationPO();
        valuation.setBondCode(bond.getBondCode());
        valuation.setBondName(bond.getBondName());
        valuation.setTsCode(StockMarketJsonParser.text(row, "ts_code", null));
        valuation.setTradeDate(tradeDate);
        valuation.setClosePrice(StockMarketJsonParser.decimal(row, "close"));
        valuation.setConversionValue(StockMarketJsonParser.decimal(row, "cb_value"));
        valuation.setPremiumRate(StockMarketJsonParser.decimal(row, "cb_over_rate"));
        valuation.setPureBondValue(StockMarketJsonParser.decimal(row, "bond_value"));
        valuation.setPureBondPremiumRate(StockMarketJsonParser.decimal(row, "bond_over_rate"));
        valuation.setYtm(StockMarketJsonParser.decimal(row, "ytm"));
        valuation.setVolume(StockMarketJsonParser.longValue(row, "vol"));
        valuation.setTurnoverAmount(StockMarketJsonParser.decimal(row, "amount"));
        valuation.setSource("tushare");
        valuation.setRawResponse(row.toString());
        valuation.setSyncedAt(syncedAt);
        return valuation;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value) || value.length() != 8) {
            return null;
        }
        return LocalDate.parse("%s-%s-%s".formatted(value.substring(0, 4), value.substring(4, 6), value.substring(6, 8)));
    }
}
