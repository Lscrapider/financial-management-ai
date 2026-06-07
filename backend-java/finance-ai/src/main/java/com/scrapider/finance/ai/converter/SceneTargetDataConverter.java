package com.scrapider.finance.ai.converter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SceneTargetDataConverter {

    private static final List<String> MESSAGE_OMITTED_FIELDS =
            List.of("id", "rawResponse", "createdAt", "updatedAt");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private SceneTargetDataConverter() {
    }

    public static SceneAnalysisMessageDTO message(
            String taskNo,
            SceneAnalysisSubmitParam param,
            SceneAnalysisTargetDTO target,
            Map<String, Object> marketData,
            Map<String, Object> valuationData,
            Map<String, Object> industryData,
            List<Map<String, Object>> valuationHistory,
            List<Map<String, Object>> financialIndicators,
            List<Map<String, Object>> dividendHistory,
            List<Map<String, Object>> dailyKlines,
            List<Map<String, Object>> weeklyKlines,
            List<Map<String, Object>> monthlyKlines,
            List<Map<String, Object>> intradayData,
            Map<String, Object> assetSpecificData,
            List<String> missing) {
        return new SceneAnalysisMessageDTO(
                taskNo,
                LocalDateTime.now(),
                normalizeReportType(param.reportType()),
                param.totalChunks(),
                target,
                new SceneAnalysisConfigDTO(
                        StrUtil.blankToDefault(param.configProfile(), "system_recommended"),
                        SceneAnalysisUserConfigParam.effective(param.userOverrides(), target.type())),
                marketData,
                valuationData,
                industryData,
                valuationHistory,
                financialIndicators,
                dividendHistory,
                dailyKlines,
                weeklyKlines,
                monthlyKlines,
                intradayData,
                assetSpecificData,
                dataCompleteness(missing));
    }

    public static SceneAnalysisTargetDTO stockTarget(
            String stockCode,
            String targetName,
            StockQuoteSnapshotPO quote,
            StockConfigPO config) {
        return new SceneAnalysisTargetDTO(
                "STOCK",
                stockCode,
                targetName,
                firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
    }

    public static SceneAnalysisTargetDTO indexTarget(
            String indexCode,
            String targetName,
            IndexQuoteSnapshotPO quote,
            IndexConfigPO config) {
        return new SceneAnalysisTargetDTO(
                "INDEX",
                indexCode,
                targetName,
                firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
    }

    public static SceneAnalysisTargetDTO bondTarget(
            String bondCode,
            String targetName,
            BondQuoteSnapshotPO quote,
            BondConfigPO config) {
        return new SceneAnalysisTargetDTO(
                "CONVERTIBLE_BOND",
                bondCode,
                targetName,
                firstNotBlank(quote == null ? null : quote.getSecid(), config == null ? null : config.getSecid()),
                firstNotBlank(
                        quote == null ? null : quote.getMarketCode(),
                        config == null ? null : config.getMarketCode()),
                firstNotBlank(
                        quote == null ? null : quote.getExchangeCode(),
                        config == null ? null : config.getExchangeCode()));
    }

    public static Map<String, Object> toMap(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return Map.of();
        }
        return compactMap(objectMapper.convertValue(value, MAP_TYPE));
    }

    public static List<Map<String, Object>> toMapList(ObjectMapper objectMapper, List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(value -> toMap(objectMapper, value)).toList();
    }

    public static Map<String, Object> stockValuationData(StockQuoteSnapshotPO quote) {
        if (quote == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("peTtm", quote.getPeTtm());
        data.put("peDynamic", quote.getPeDynamic());
        data.put("peStatic", quote.getPeStatic());
        data.put("pbRatio", quote.getPbRatio());
        data.put("totalMarketValue", quote.getTotalMarketValue());
        data.put("floatMarketValue", quote.getFloatMarketValue());
        return compactMap(data);
    }

    public static Map<String, Object> stockAssetSpecificData(ObjectMapper objectMapper, StockSceneDataDTO sceneData) {
        if (sceneData == null) {
            return Map.of("stock", Map.of());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("industryData", toMap(objectMapper, sceneData.industryInfo()));
        data.put("valuationHistory", toMapList(objectMapper, sceneData.valuationHistory()));
        data.put("financialIndicators", toMapList(objectMapper, sceneData.financialIndicators()));
        data.put("dividendHistory", toMapList(objectMapper, sceneData.dividendHistory()));
        return Map.of("stock", compactMap(data));
    }

    public static Map<String, Long> freeSharesByDate(List<StockValuationHistoryPO> valuationHistory) {
        Map<String, Long> result = new LinkedHashMap<>();
        valuationHistory.forEach(valuation -> {
            if (valuation.getTradeDate() != null && valuation.getFreeSharesA() != null) {
                result.put(valuation.getTradeDate().toString(), valuation.getFreeSharesA());
            }
        });
        return result;
    }

    public static Map<String, Object> dailyKlineWithTurnoverRate(
            Map<String, Object> row,
            BigDecimal turnoverRate) {
        Map<String, Object> filled = new LinkedHashMap<>(row);
        filled.put("turnoverRate", turnoverRate);
        return filled;
    }

    public static Map<String, Object> convertibleBondAssetSpecificData(
            ObjectMapper objectMapper,
            BondQuoteSnapshotPO quote,
            ConvertibleBondBasicPO basic,
            ConvertibleBondDailyValuationPO latestValuation,
            ConvertibleBondSharePO latestShare,
            List<ConvertibleBondDailyValuationPO> valuations,
            StockQuoteSnapshotPO underlyingQuote,
            BigDecimal bondPrice,
            BigDecimal conversionPrice,
            BigDecimal underlyingPrice,
            BigDecimal realtimeConversionValue,
            BigDecimal realtimePremiumRate,
            BigDecimal estimatedYtm,
            Long maturityDays) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bondPrice", bondPrice);
        data.put("turnoverRate", quote == null ? null : quote.getTurnoverRate());
        data.put("bondRating", firstNotBlank(
                basic == null ? null : basic.getRating(),
                quote == null ? null : quote.getBondRating()));
        data.put("premiumRate", realtimePremiumRate);
        data.put("premiumRateSource", realtimePremiumRate == null ? null : "quote_snapshot_calculated");
        data.put("dailyPremiumRate", latestValuation == null ? null : latestValuation.getPremiumRate());
        List<ConvertibleBondDailyValuationPO> chronologicalValuations = valuations.stream()
                .sorted(Comparator.comparing(ConvertibleBondDailyValuationPO::getTradeDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        data.put("premiumRateHistory", chronologicalValuations.stream()
                .map(ConvertibleBondDailyValuationPO::getPremiumRate)
                .toList());
        data.put("conversionValue", realtimeConversionValue);
        data.put("conversionValueSource", realtimeConversionValue == null ? null : "quote_snapshot_calculated");
        data.put("dailyConversionValue", latestValuation == null ? null : latestValuation.getConversionValue());
        data.put("conversionValueHistory", chronologicalValuations.stream()
                .map(ConvertibleBondDailyValuationPO::getConversionValue)
                .toList());
        data.put("pureBondValue", latestValuation == null ? null : latestValuation.getPureBondValue());
        data.put("ytm", firstNonNull(latestValuation == null ? null : latestValuation.getYtm(), estimatedYtm));
        data.put("remainingSize", firstNonNull(
                basic == null ? null : basic.getRemainingSize(),
                latestShare == null ? null : latestShare.getRemainingSize()));
        data.put("maturityDays", maturityDays);
        data.put("redeemStatus", "DATA_INSUFFICIENT");
        data.put("redeemTriggerProgress", null);
        data.put("putbackStatus", "DATA_INSUFFICIENT");
        data.put("underlyingStockCode", basic == null ? null : basic.getUnderlyingStockCode());
        data.put("underlyingStockName", basic == null ? null : basic.getUnderlyingStockName());
        data.put("conversionPrice", conversionPrice);
        data.put("maturityDate", basic == null ? null : basic.getMaturityDate());
        data.put("maturityCallPrice", basic == null ? null : basic.getMaturityCallPrice());
        data.put("couponRate", basic == null ? null : basic.getCouponRate());
        data.put("redeemClause", basic == null ? null : basic.getRedeemClause());
        data.put("putbackClause", basic == null ? null : basic.getPutbackClause());
        data.put("underlyingQuote", toMap(objectMapper, underlyingQuote));
        data.put("underlyingDailyKlines", List.of());
        data.put("underlyingPrice", underlyingPrice);
        data.put("underlyingChangePct", underlyingQuote == null ? null : underlyingQuote.getChangePercent());
        data.put("underlyingTrendScore", null);
        data.put("stockBondLinkage", null);
        return Map.of("convertibleBond", compactMap(data));
    }

    public static Map<String, Object> compactMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StrUtil.isBlank(key) || MESSAGE_OMITTED_FIELDS.contains(key) || value == null) {
                return;
            }
            Object compactValue = compactValue(value);
            if (compactValue != null) {
                result.put(key, compactValue);
            }
        });
        return result;
    }

    private static Object compactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                if (!(nestedKey instanceof String key)
                        || StrUtil.isBlank(key)
                        || MESSAGE_OMITTED_FIELDS.contains(key)
                        || nestedValue == null) {
                    return;
                }
                Object compactNestedValue = compactValue(nestedValue);
                if (compactNestedValue != null) {
                    nested.put(key, compactNestedValue);
                }
            });
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(SceneTargetDataConverter::compactValue)
                    .filter(item -> item != null)
                    .toList();
        }
        return value;
    }

    private static String normalizeReportType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode();
        }
        return SceneAnalysisReportTypeEnum.of(reportType.trim()).getCode();
    }

    private static Map<String, Object> dataCompleteness(List<String> missing) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complete", missing == null || missing.isEmpty());
        result.put("missing", missing == null ? List.of() : missing);
        return result;
    }

    private static String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
