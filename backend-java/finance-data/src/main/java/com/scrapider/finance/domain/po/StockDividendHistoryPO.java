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
@TableName("stock_dividend_history")
public class StockDividendHistoryPO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secucode;
    private LocalDate reportDate;
    private LocalDate exDividendDate;
    private LocalDate planNoticeDate;
    private LocalDate equityRecordDate;
    private BigDecimal pretaxBonusRmb;
    private BigDecimal dividendRatio;
    private String implPlanProfile;
    private String assignProgress;
    private BigDecimal basicEps;
    private BigDecimal bvps;
    private BigDecimal parentNetProfitYoy;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<StockDividendHistoryPO> fromEastMoneyResponse(StockConfigPO stockConfig, JsonNode response) {
        JsonNode rows = response.path("result").path("data");
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromEastMoneyRow(stockConfig, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static StockDividendHistoryPO fromEastMoneyRow(
            StockConfigPO stockConfig,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate reportDate = date(row, "REPORT_DATE");
        LocalDate exDividendDate = date(row, "EX_DIVIDEND_DATE");
        if (reportDate == null && exDividendDate == null) {
            return null;
        }

        StockDividendHistoryPO dividend = new StockDividendHistoryPO();
        dividend.setStockCode(stockConfig.getStockCode());
        dividend.setStockName(StockMarketJsonParser.text(row, "SECURITY_NAME_ABBR", stockConfig.getStockName()));
        dividend.setSecucode(StockMarketJsonParser.text(row, "SECUCODE", stockConfig.getStockCode()));
        dividend.setReportDate(reportDate);
        dividend.setExDividendDate(exDividendDate);
        dividend.setPlanNoticeDate(date(row, "PLAN_NOTICE_DATE"));
        dividend.setEquityRecordDate(date(row, "EQUITY_RECORD_DATE"));
        dividend.setPretaxBonusRmb(StockMarketJsonParser.decimal(row, "PRETAX_BONUS_RMB"));
        dividend.setDividendRatio(StockMarketJsonParser.decimal(row, "DIVIDENT_RATIO"));
        dividend.setImplPlanProfile(StockMarketJsonParser.text(row, "IMPL_PLAN_PROFILE", null));
        dividend.setAssignProgress(StockMarketJsonParser.text(row, "ASSIGN_PROGRESS", null));
        dividend.setBasicEps(StockMarketJsonParser.decimal(row, "BASIC_EPS"));
        dividend.setBvps(StockMarketJsonParser.decimal(row, "BVPS"));
        dividend.setParentNetProfitYoy(StockMarketJsonParser.decimal(row, "PNP_YOY_RATIO"));
        dividend.setSource("eastmoney");
        dividend.setRawResponse(row.toString());
        dividend.setSyncedAt(syncedAt);
        return dividend;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10));
    }
}
