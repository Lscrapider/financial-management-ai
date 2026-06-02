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
@TableName("stock_valuation_history")
public class StockValuationHistoryPO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secid;
    private String secucode;
    private LocalDate tradeDate;
    private String boardCode;
    private String boardName;
    private BigDecimal totalMarketCap;
    private BigDecimal floatMarketCap;
    private BigDecimal closePrice;
    private BigDecimal changeRate;
    private Long totalShares;
    private Long freeSharesA;
    private BigDecimal peTtm;
    private BigDecimal peLar;
    private BigDecimal pbMrq;
    private BigDecimal psTtm;
    private BigDecimal pegCar;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<StockValuationHistoryPO> fromEastMoneyResponse(StockConfigPO stockConfig, JsonNode response) {
        JsonNode rows = response.path("result").path("data");
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromEastMoneyRow(stockConfig, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static StockValuationHistoryPO fromEastMoneyRow(
            StockConfigPO stockConfig,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate tradeDate = date(row, "TRADE_DATE");
        if (tradeDate == null) {
            return null;
        }

        StockValuationHistoryPO valuation = new StockValuationHistoryPO();
        valuation.setStockCode(stockConfig.getStockCode());
        valuation.setStockName(StockMarketJsonParser.text(row, "SECURITY_NAME_ABBR", stockConfig.getStockName()));
        valuation.setSecid(stockConfig.getSecid());
        valuation.setSecucode(StockMarketJsonParser.text(row, "SECUCODE", stockConfig.getStockCode()));
        valuation.setTradeDate(tradeDate);
        valuation.setBoardCode(StockMarketJsonParser.text(row, "BOARD_CODE", null));
        valuation.setBoardName(StockMarketJsonParser.text(row, "BOARD_NAME", null));
        valuation.setTotalMarketCap(StockMarketJsonParser.decimal(row, "TOTAL_MARKET_CAP"));
        valuation.setFloatMarketCap(StockMarketJsonParser.decimal(row, "NOTLIMITED_MARKETCAP_A"));
        valuation.setClosePrice(StockMarketJsonParser.decimal(row, "CLOSE_PRICE"));
        valuation.setChangeRate(StockMarketJsonParser.decimal(row, "CHANGE_RATE"));
        valuation.setTotalShares(StockMarketJsonParser.longValue(row, "TOTAL_SHARES"));
        valuation.setFreeSharesA(StockMarketJsonParser.longValue(row, "FREE_SHARES_A"));
        valuation.setPeTtm(StockMarketJsonParser.decimal(row, "PE_TTM"));
        valuation.setPeLar(StockMarketJsonParser.decimal(row, "PE_LAR"));
        valuation.setPbMrq(StockMarketJsonParser.decimal(row, "PB_MRQ"));
        valuation.setPsTtm(StockMarketJsonParser.decimal(row, "PS_TTM"));
        valuation.setPegCar(StockMarketJsonParser.decimal(row, "PEG_CAR"));
        valuation.setSource("eastmoney");
        valuation.setRawResponse(row.toString());
        valuation.setSyncedAt(syncedAt);
        return valuation;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10));
    }
}
