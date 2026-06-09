package com.scrapider.finance.ai.converter;

import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
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

    public static Map<String, Object> bondTarget(String targetCode, String targetName) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("targetType", "bond");
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
        valuation.put("dividend", stockDividend(dividends, latest == null ? null : latest.getClosePrice()));
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

    public static Map<String, Object> convertibleBondBasic(ConvertibleBondBasicPO basic) {
        if (basic == null) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("underlyingStockCode", basic.getUnderlyingStockCode());
        row.put("underlyingStockName", basic.getUnderlyingStockName());
        row.put("rating", basic.getRating());
        row.put("issueSize", basic.getIssueSize());
        row.put("remainingSize", basic.getRemainingSize());
        row.put("firstConversionPrice", basic.getFirstConversionPrice());
        row.put("conversionPrice", basic.getConversionPrice());
        row.put("valueDate", basic.getValueDate());
        row.put("maturityDate", basic.getMaturityDate());
        row.put("maturityCallPrice", basic.getMaturityCallPrice());
        row.put("couponRate", basic.getCouponRate());
        row.put("payPerYear", basic.getPayPerYear());
        row.put("conversionStartDate", basic.getConversionStartDate());
        row.put("conversionEndDate", basic.getConversionEndDate());
        row.put("redeemClause", basic.getRedeemClause());
        row.put("putbackClause", basic.getPutbackClause());
        row.put("resetClause", basic.getResetClause());
        return compact(row);
    }

    public static List<Map<String, Object>> convertibleBondValuationHistory(
            List<ConvertibleBondDailyValuationPO> valuations) {
        if (valuations == null || valuations.isEmpty()) {
            return List.of();
        }
        return valuations.stream()
                .map(AiAgentDomainToolDataConverter::convertibleBondValuation)
                .toList();
    }

    public static List<Map<String, Object>> convertibleBondShareChanges(List<ConvertibleBondSharePO> shares) {
        if (shares == null || shares.isEmpty()) {
            return List.of();
        }
        return shares.stream()
                .map(AiAgentDomainToolDataConverter::convertibleBondShare)
                .toList();
    }

    public static List<Map<String, Object>> sceneReports(List<SceneAnalysisReportPO> reports, int maxTextChars) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .map(report -> sceneReport(report, maxTextChars))
                .toList();
    }

    public static List<Map<String, Object>> sceneReportSummaries(List<SceneAnalysisReportHistoryDTO> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .map(AiAgentDomainToolDataConverter::sceneReportSummary)
                .toList();
    }

    public static Map<String, Object> sceneReport(SceneAnalysisReportPO report, int maxTextChars) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (report == null) {
            return row;
        }
        row.put("reportId", report.getId());
        row.put("targetType", report.getTargetType());
        row.put("targetCode", report.getTargetCode());
        row.put("targetName", report.getTargetName());
        row.put("reportType", report.getReportType());
        row.put("generationType", report.getGenerationType());
        row.put("versionNo", report.getVersionNo());
        row.put("status", report.getStatus());
        row.put("generatedAt", report.getGeneratedAt());
        row.put("reportText", truncate(report.getReportText(), maxTextChars));
        return compact(row);
    }

    private static Map<String, Object> sceneReportSummary(SceneAnalysisReportHistoryDTO report) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", report.getReportId());
        row.put("targetType", report.getTargetType());
        row.put("targetCode", report.getTargetCode());
        row.put("targetName", report.getTargetName());
        row.put("reportType", report.getReportType());
        row.put("generationType", report.getGenerationType());
        row.put("versionNo", report.getVersionNo());
        row.put("status", report.getStatus());
        row.put("generatedAt", report.getGeneratedAt());
        row.put("createdAt", report.getCreatedAt());
        return compact(row);
    }

    private static Map<String, Object> convertibleBondValuation(ConvertibleBondDailyValuationPO valuation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tradeDate", valuation.getTradeDate());
        row.put("conversionValue", valuation.getConversionValue());
        row.put("premiumRate", valuation.getPremiumRate());
        row.put("pureBondValue", valuation.getPureBondValue());
        row.put("pureBondPremiumRate", valuation.getPureBondPremiumRate());
        row.put("ytm", valuation.getYtm());
        return compact(row);
    }

    private static Map<String, Object> convertibleBondShare(ConvertibleBondSharePO share) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("endDate", share.getEndDate());
        row.put("issueSize", share.getIssueSize());
        row.put("conversionPrice", share.getConversionPrice());
        row.put("conversionValue", share.getConversionValue());
        row.put("conversionVolume", share.getConversionVolume());
        row.put("conversionRatio", share.getConversionRatio());
        row.put("remainingSize", share.getRemainingSize());
        return compact(row);
    }

    private static Map<String, Object> stockDividend(List<StockDividendHistoryPO> dividends, BigDecimal currentPrice) {
        Map<String, Object> dividend = new LinkedHashMap<>();
        if (dividends != null && !dividends.isEmpty()) {
            Map<String, Object> latest = stockDividendLatest(dividends.get(0));
            dividend.put("latest", latest);
            dividend.put("estimatedDividendYieldPct", dividendYieldPct(dividends.get(0), currentPrice));
            dividend.put("historySummary", dividendYieldHistorySummary(dividends, currentPrice));
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

    private static Map<String, Object> dividendYieldHistorySummary(
            List<StockDividendHistoryPO> dividends,
            BigDecimal currentPrice) {
        List<BigDecimal> values = dividends == null ? List.of() : dividends.stream()
                .map(dividend -> dividendYieldPct(dividend, currentPrice))
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
        summary.put("yieldPctLatest", latest);
        summary.put("count", values.size());
        summary.put("yieldPctMin", min);
        summary.put("yieldPctMax", max);
        summary.put("yieldPctAverage", sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros());
        summary.put("yieldPctPercentileRank", percentileRank(latest, values));
        return compact(summary);
    }

    private static BigDecimal dividendYieldPct(StockDividendHistoryPO dividend, BigDecimal currentPrice) {
        if (dividend == null
                || dividend.getPretaxBonusRmb() == null
                || currentPrice == null
                || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return dividend.getPretaxBonusRmb()
                .divide(BigDecimal.TEN, 10, RoundingMode.HALF_UP)
                .divide(currentPrice, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
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
        BigDecimal latest = rows == null || rows.isEmpty() ? null : valueExtractor.apply(rows.get(0));
        List<BigDecimal> values = rows == null ? List.of() : rows.stream()
                .map(valueExtractor)
                .filter(value -> value != null)
                .toList();
        if (latest == null && values.isEmpty()) {
            return Map.of();
        }
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latest", latest);
        summary.put("count", values.size());
        summary.put("min", min);
        summary.put("max", max);
        summary.put("average", values.isEmpty()
                ? null
                : sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP).stripTrailingZeros());
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

    private static String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    public static boolean isReportTextTruncated(SceneAnalysisReportPO report, int maxChars) {
        if (report == null) {
            return false;
        }
        return isTruncated(report.getReportText(), maxChars);
    }

    private static boolean isTruncated(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return false;
        }
        return value.length() > maxChars;
    }
}
