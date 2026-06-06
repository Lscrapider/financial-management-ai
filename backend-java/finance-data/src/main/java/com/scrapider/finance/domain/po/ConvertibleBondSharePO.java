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
@TableName("convertible_bond_share")
public class ConvertibleBondSharePO {

    private Long id;
    private String bondCode;
    private String bondName;
    private String tsCode;
    private LocalDate endDate;
    private BigDecimal issueSize;
    private BigDecimal conversionPrice;
    private BigDecimal conversionValue;
    private BigDecimal conversionVolume;
    private BigDecimal conversionRatio;
    private BigDecimal remainingSize;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<ConvertibleBondSharePO> fromTushareRows(BondConfigPO bond, JsonNode rows) {
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(bond, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static ConvertibleBondSharePO fromTushareRow(BondConfigPO bond, JsonNode row, LocalDateTime syncedAt) {
        LocalDate endDate = date(row, "end_date");
        if (endDate == null) {
            return null;
        }
        ConvertibleBondSharePO share = new ConvertibleBondSharePO();
        share.setBondCode(bond.getBondCode());
        share.setBondName(bond.getBondName());
        share.setTsCode(StockMarketJsonParser.text(row, "ts_code", null));
        share.setEndDate(endDate);
        share.setIssueSize(StockMarketJsonParser.decimal(row, "issue_size"));
        share.setConversionPrice(StockMarketJsonParser.decimal(row, "convert_price"));
        share.setConversionValue(StockMarketJsonParser.decimal(row, "convert_val"));
        share.setConversionVolume(StockMarketJsonParser.decimal(row, "convert_vol"));
        share.setConversionRatio(StockMarketJsonParser.decimal(row, "convert_ratio"));
        share.setRemainingSize(StockMarketJsonParser.decimal(row, "remain_size"));
        share.setSource("tushare");
        share.setRawResponse(row.toString());
        share.setSyncedAt(syncedAt);
        return share;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value) || value.length() != 8) {
            return null;
        }
        return LocalDate.parse("%s-%s-%s".formatted(value.substring(0, 4), value.substring(4, 6), value.substring(6, 8)));
    }
}
