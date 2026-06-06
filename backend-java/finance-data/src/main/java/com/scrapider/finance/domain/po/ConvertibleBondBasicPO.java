package com.scrapider.finance.domain.po;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.util.StockMarketJsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.Data;

@Data
@TableName("convertible_bond_basic")
public class ConvertibleBondBasicPO {

    private Long id;
    private String bondCode;
    private String bondName;
    private String tsCode;
    private String underlyingStockCode;
    private String underlyingStockName;
    private String rating;
    private BigDecimal issueSize;
    private BigDecimal remainingSize;
    private BigDecimal firstConversionPrice;
    private BigDecimal conversionPrice;
    private LocalDate valueDate;
    private LocalDate maturityDate;
    private BigDecimal maturityCallPrice;
    private String couponRate;
    private Integer payPerYear;
    private LocalDate conversionStartDate;
    private LocalDate conversionEndDate;
    private String redeemClause;
    private String putbackClause;
    private String resetClause;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ConvertibleBondBasicPO fromTushareRows(BondConfigPO bond, JsonNode rows) {
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(bond, row, syncedAt))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static ConvertibleBondBasicPO fromTushareRow(
            BondConfigPO bond,
            JsonNode row,
            LocalDateTime syncedAt) {
        String tsCode = StockMarketJsonParser.text(row, "ts_code", null);
        if (StrUtil.isBlank(tsCode)) {
            return null;
        }
        ConvertibleBondBasicPO basic = new ConvertibleBondBasicPO();
        basic.setBondCode(bond.getBondCode());
        basic.setBondName(StockMarketJsonParser.text(row, "bond_short_name", bond.getBondName()));
        basic.setTsCode(tsCode);
        basic.setUnderlyingStockCode(StockMarketJsonParser.text(row, "stk_code", null));
        basic.setUnderlyingStockName(StockMarketJsonParser.text(row, "stk_short_name", null));
        basic.setRating(StockMarketJsonParser.text(row, "newest_rating",
                StockMarketJsonParser.text(row, "issue_rating", null)));
        basic.setIssueSize(StockMarketJsonParser.decimal(row, "issue_size"));
        basic.setRemainingSize(StockMarketJsonParser.decimal(row, "remain_size"));
        basic.setFirstConversionPrice(StockMarketJsonParser.decimal(row, "first_conv_price"));
        basic.setConversionPrice(StockMarketJsonParser.decimal(row, "conv_price"));
        basic.setValueDate(date(row, "value_date"));
        basic.setMaturityDate(date(row, "maturity_date"));
        basic.setMaturityCallPrice(StockMarketJsonParser.decimal(row, "maturity_call_price"));
        basic.setCouponRate(StockMarketJsonParser.text(row, "coupon_rate", null));
        basic.setPayPerYear(StockMarketJsonParser.intValue(row, "pay_per_year"));
        basic.setConversionStartDate(date(row, "conv_start_date"));
        basic.setConversionEndDate(date(row, "conv_end_date"));
        basic.setRedeemClause(StockMarketJsonParser.text(row, "call_clause", null));
        basic.setPutbackClause(StockMarketJsonParser.text(row, "put_clause", null));
        basic.setResetClause(StockMarketJsonParser.text(row, "reset_clause", null));
        basic.setSource("tushare");
        basic.setRawResponse(row.toString());
        basic.setSyncedAt(syncedAt);
        return basic;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value.length() == 8
                ? "%s-%s-%s".formatted(value.substring(0, 4), value.substring(4, 6), value.substring(6, 8))
                : value.substring(0, 10));
    }
}
