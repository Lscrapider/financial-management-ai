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
@TableName("stock_financial_indicator")
public class StockFinancialIndicatorPO {

    private Long id;
    private String stockCode;
    private String stockName;
    private String secucode;
    private String orgType;
    private LocalDate reportDate;
    private String reportType;
    private String reportDateName;
    private LocalDate noticeDate;
    private BigDecimal epsBasic;
    private BigDecimal bps;
    private BigDecimal totalOperateRevenue;
    private BigDecimal parentNetProfit;
    private BigDecimal totalOperateRevenueYoy;
    private BigDecimal parentNetProfitYoy;
    private BigDecimal roeWeighted;
    private BigDecimal debtAssetRatio;
    private BigDecimal totalDeposits;
    private BigDecimal grossLoans;
    private BigDecimal loanToDepositRatio;
    private BigDecimal capitalAdequacyRatio;
    private BigDecimal coreTier1CapitalAdequacyRatio;
    private BigDecimal firstAdequacyRatio;
    private BigDecimal nonPerformingLoanRatio;
    private BigDecimal provisionCoverageRatio;
    private BigDecimal netInterestSpread;
    private BigDecimal netInterestMargin;
    private BigDecimal loanProvisionRatio;
    private String source;
    private String rawResponse;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static List<StockFinancialIndicatorPO> fromEastMoneyResponse(StockConfigPO stockConfig, JsonNode response) {
        JsonNode rows = response.path("result").path("data");
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromEastMoneyRow(stockConfig, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<StockFinancialIndicatorPO> fromTushareRows(StockConfigPO stockConfig, JsonNode rows) {
        LocalDateTime syncedAt = LocalDateTime.now();
        return StreamSupport.stream(rows.spliterator(), false)
                .map(row -> fromTushareRow(stockConfig, row, syncedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private static StockFinancialIndicatorPO fromEastMoneyRow(
            StockConfigPO stockConfig,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate reportDate = date(row, "REPORT_DATE");
        if (reportDate == null) {
            return null;
        }

        StockFinancialIndicatorPO indicator = new StockFinancialIndicatorPO();
        indicator.setStockCode(stockConfig.getStockCode());
        indicator.setStockName(StockMarketJsonParser.text(row, "SECURITY_NAME_ABBR", stockConfig.getStockName()));
        indicator.setSecucode(StockMarketJsonParser.text(row, "SECUCODE", stockConfig.getStockCode()));
        indicator.setOrgType(StockMarketJsonParser.text(row, "ORG_TYPE", null));
        indicator.setReportDate(reportDate);
        indicator.setReportType(StockMarketJsonParser.text(row, "REPORT_TYPE", null));
        indicator.setReportDateName(StockMarketJsonParser.text(row, "REPORT_DATE_NAME", null));
        indicator.setNoticeDate(date(row, "NOTICE_DATE"));
        indicator.setEpsBasic(StockMarketJsonParser.decimal(row, "EPSJB"));
        indicator.setBps(StockMarketJsonParser.decimal(row, "BPS"));
        indicator.setTotalOperateRevenue(StockMarketJsonParser.decimal(row, "TOTALOPERATEREVE"));
        indicator.setParentNetProfit(StockMarketJsonParser.decimal(row, "PARENTNETPROFIT"));
        indicator.setTotalOperateRevenueYoy(StockMarketJsonParser.decimal(row, "TOTALOPERATEREVETZ"));
        indicator.setParentNetProfitYoy(StockMarketJsonParser.decimal(row, "PARENTNETPROFITTZ"));
        indicator.setRoeWeighted(StockMarketJsonParser.decimal(row, "ROEJQ"));
        indicator.setDebtAssetRatio(StockMarketJsonParser.decimal(row, "ZCFZL"));
        indicator.setTotalDeposits(StockMarketJsonParser.decimal(row, "TOTALDEPOSITS"));
        indicator.setGrossLoans(StockMarketJsonParser.decimal(row, "GROSSLOANS"));
        indicator.setLoanToDepositRatio(StockMarketJsonParser.decimal(row, "LTDRR"));
        indicator.setCapitalAdequacyRatio(StockMarketJsonParser.decimal(row, "NEWCAPITALADER"));
        indicator.setCoreTier1CapitalAdequacyRatio(StockMarketJsonParser.decimal(row, "HXYJBCZL"));
        indicator.setFirstAdequacyRatio(StockMarketJsonParser.decimal(row, "FIRST_ADEQUACY_RATIO"));
        indicator.setNonPerformingLoanRatio(StockMarketJsonParser.decimal(row, "NONPERLOAN"));
        indicator.setProvisionCoverageRatio(StockMarketJsonParser.decimal(row, "BLDKBBL"));
        indicator.setNetInterestSpread(StockMarketJsonParser.decimal(row, "NET_INTEREST_SPREAD"));
        indicator.setNetInterestMargin(StockMarketJsonParser.decimal(row, "NET_INTEREST_MARGIN"));
        indicator.setLoanProvisionRatio(StockMarketJsonParser.decimal(row, "LOAN_PROVISION_RATIO"));
        indicator.setSource("eastmoney");
        indicator.setRawResponse(row.toString());
        indicator.setSyncedAt(syncedAt);
        return indicator;
    }

    private static StockFinancialIndicatorPO fromTushareRow(
            StockConfigPO stockConfig,
            JsonNode row,
            LocalDateTime syncedAt) {
        LocalDate reportDate = date(row, "end_date");
        if (reportDate == null) {
            return null;
        }

        StockFinancialIndicatorPO indicator = new StockFinancialIndicatorPO();
        indicator.setStockCode(stockConfig.getStockCode());
        indicator.setStockName(stockConfig.getStockName());
        indicator.setSecucode(StockMarketJsonParser.text(row, "ts_code", stockConfig.getStockCode()));
        indicator.setReportDate(reportDate);
        indicator.setReportType(StockMarketJsonParser.text(row, "report_type", "tushare"));
        indicator.setReportDateName(StockMarketJsonParser.text(row, "end_date", null));
        indicator.setNoticeDate(date(row, "ann_date"));
        indicator.setEpsBasic(StockMarketJsonParser.decimal(row, "eps"));
        indicator.setBps(StockMarketJsonParser.decimal(row, "bps"));
        indicator.setTotalOperateRevenue(StockMarketJsonParser.decimal(row, "revenue"));
        indicator.setParentNetProfit(StockMarketJsonParser.decimal(row, "profit_to_gr"));
        indicator.setParentNetProfitYoy(StockMarketJsonParser.decimal(row, "q_profit_yoy"));
        indicator.setRoeWeighted(StockMarketJsonParser.decimal(row, "roe"));
        indicator.setDebtAssetRatio(StockMarketJsonParser.decimal(row, "debt_to_assets"));
        indicator.setSource("tushare");
        indicator.setRawResponse(row.toString());
        indicator.setSyncedAt(syncedAt);
        return indicator;
    }

    private static LocalDate date(JsonNode row, String fieldName) {
        String value = StockMarketJsonParser.text(row, fieldName, null);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        if (value.length() == 8) {
            return LocalDate.parse("%s-%s-%s".formatted(
                    value.substring(0, 4),
                    value.substring(4, 6),
                    value.substring(6, 8)));
        }
        return LocalDate.parse(value.substring(0, 10));
    }
}
