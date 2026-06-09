package com.scrapider.finance.ai.converter;

import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class AiAgentDomainToolDataConverter {

    private AiAgentDomainToolDataConverter() {
    }

    public static Map<String, Object> stockTarget(String targetCode, String targetName) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "stock");
        target.put("targetCode", targetCode);
        target.put("targetName", targetName);
        return compact(target);
    }

    public static Map<String, Object> stockValuation(
            List<StockValuationHistoryPO> valuations,
            List<StockDividendHistoryPO> dividends) {
        StockValuationHistoryPO latest = valuations == null || valuations.isEmpty() ? null : valuations.get(0);
        Map<String, Object> current = new LinkedHashMap<>();
        if (latest != null) {
            current.put("peTtm", latest.getPeTtm());
            current.put("pbRatio", latest.getPbMrq());
        }

        Map<String, Object> historySummary = new LinkedHashMap<>();
        historySummary.put("peTtm", distributionSummary(valuations, StockValuationHistoryPO::getPeTtm));
        historySummary.put("pbMrq", distributionSummary(valuations, StockValuationHistoryPO::getPbMrq));

        Map<String, Object> valuation = new LinkedHashMap<>();
        valuation.put("current", compact(current));
        valuation.put("historySummary", compact(historySummary));
        valuation.put("dividend", stockDividend(dividends));
        return compact(valuation);
    }

    public static List<Map<String, Object>> stockFinancialIndicators(List<StockFinancialIndicatorPO> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return List.of();
        }
        return indicators.stream()
                .map(AiAgentDomainToolDataConverter::stockFinancialIndicator)
                .toList();
    }

    private static Map<String, Object> stockDividend(List<StockDividendHistoryPO> dividends) {
        Map<String, Object> dividend = new LinkedHashMap<>();
        if (dividends != null && !dividends.isEmpty()) {
            dividend.put("latest", stockDividendLatest(dividends.get(0)));

            Map<String, Object> historySummary = new LinkedHashMap<>();
            historySummary.put("pretaxBonusRmb", distributionSummary(dividends, StockDividendHistoryPO::getPretaxBonusRmb));
            historySummary.put("dividendRatio", distributionSummary(dividends, StockDividendHistoryPO::getDividendRatio));
            dividend.put("historySummary", compact(historySummary));
        }
        return compact(dividend);
    }

    private static Map<String, Object> stockDividendLatest(StockDividendHistoryPO dividend) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportDate", dividend.getReportDate());
        row.put("exDividendDate", dividend.getExDividendDate());
        row.put("pretaxBonusRmb", dividend.getPretaxBonusRmb());
        row.put("dividendRatio", dividend.getDividendRatio());
        row.put("implPlanProfile", dividend.getImplPlanProfile());
        row.put("assignProgress", dividend.getAssignProgress());
        return compact(row);
    }

    private static Map<String, Object> stockFinancialIndicator(StockFinancialIndicatorPO indicator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportDate", indicator.getReportDate());
        row.put("reportType", indicator.getReportType());
        row.put("reportDateName", indicator.getReportDateName());
        row.put("noticeDate", indicator.getNoticeDate());
        row.put("epsBasic", indicator.getEpsBasic());
        row.put("bps", indicator.getBps());
        row.put("totalOperateRevenue", indicator.getTotalOperateRevenue());
        row.put("parentNetProfit", indicator.getParentNetProfit());
        row.put("totalOperateRevenueYoy", indicator.getTotalOperateRevenueYoy());
        row.put("parentNetProfitYoy", indicator.getParentNetProfitYoy());
        row.put("roeWeighted", indicator.getRoeWeighted());
        row.put("debtAssetRatio", indicator.getDebtAssetRatio());
        row.put("totalDeposits", indicator.getTotalDeposits());
        row.put("grossLoans", indicator.getGrossLoans());
        row.put("loanToDepositRatio", indicator.getLoanToDepositRatio());
        row.put("capitalAdequacyRatio", indicator.getCapitalAdequacyRatio());
        row.put("coreTier1CapitalAdequacyRatio", indicator.getCoreTier1CapitalAdequacyRatio());
        row.put("firstAdequacyRatio", indicator.getFirstAdequacyRatio());
        row.put("nonPerformingLoanRatio", indicator.getNonPerformingLoanRatio());
        row.put("provisionCoverageRatio", indicator.getProvisionCoverageRatio());
        row.put("netInterestSpread", indicator.getNetInterestSpread());
        row.put("netInterestMargin", indicator.getNetInterestMargin());
        row.put("loanProvisionRatio", indicator.getLoanProvisionRatio());
        return compact(row);
    }

    private static <T> Map<String, Object> distributionSummary(List<T> rows, Function<T, BigDecimal> valueExtractor) {
        List<BigDecimal> values = rows == null ? List.of() : rows.stream()
                .map(valueExtractor)
                .filter(value -> value != null)
                .toList();
        if (values.isEmpty()) {
            return Map.of();
        }
        BigDecimal latest = values.get(0);
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latest", latest);
        summary.put("count", values.size());
        summary.put("min", min);
        summary.put("max", max);
        summary.put("average", sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP).stripTrailingZeros());
        summary.put("percentileRank", percentileRank(latest, values));
        return compact(summary);
    }

    private static BigDecimal percentileRank(BigDecimal latest, List<BigDecimal> values) {
        if (latest == null || values == null || values.isEmpty()) {
            return null;
        }
        long lessOrEqualCount = values.stream()
                .filter(value -> value.compareTo(latest) <= 0)
                .count();
        return BigDecimal.valueOf(lessOrEqualCount)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static Map<String, Object> compact(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (value == null || value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
                return;
            }
            result.put(key, value);
        });
        return result;
    }
}
