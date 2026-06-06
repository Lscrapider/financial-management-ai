package com.scrapider.finance.ai.service.impl.scene;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractSceneTargetDataProvider {

    protected static final int MARKET_KLINE_LIMIT = 250;
    private static final List<String> MESSAGE_OMITTED_FIELDS =
            List.of("id", "rawResponse", "createdAt", "updatedAt");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    protected AbstractSceneTargetDataProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected SceneAnalysisMessageDTO message(
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
                this.normalizeReportType(param.reportType()),
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
                this.dataCompleteness(missing));
    }

    protected String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    protected Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return this.compactMap(this.objectMapper.convertValue(value, MAP_TYPE));
    }

    protected List<Map<String, Object>> toMapList(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(this::toMap).toList();
    }

    protected Map<String, Object> compactMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StrUtil.isBlank(key) || MESSAGE_OMITTED_FIELDS.contains(key) || value == null) {
                return;
            }
            Object compactValue = this.compactValue(value);
            if (compactValue != null) {
                result.put(key, compactValue);
            }
        });
        return result;
    }

    private Object compactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                if (!(nestedKey instanceof String key)
                        || StrUtil.isBlank(key)
                        || MESSAGE_OMITTED_FIELDS.contains(key)
                        || nestedValue == null) {
                    return;
                }
                Object compactNestedValue = this.compactValue(nestedValue);
                if (compactNestedValue != null) {
                    nested.put(key, compactNestedValue);
                }
            });
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::compactValue)
                    .filter(item -> item != null)
                    .toList();
        }
        return value;
    }

    private String normalizeReportType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode();
        }
        return SceneAnalysisReportTypeEnum.of(reportType.trim()).getCode();
    }

    private Map<String, Object> dataCompleteness(List<String> missing) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complete", missing == null || missing.isEmpty());
        result.put("missing", missing == null ? List.of() : missing);
        return result;
    }
}
