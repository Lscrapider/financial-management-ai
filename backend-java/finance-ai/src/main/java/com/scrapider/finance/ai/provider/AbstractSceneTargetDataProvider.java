package com.scrapider.finance.ai.provider;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.converter.SceneTargetDataConverter;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import java.util.List;
import java.util.Map;

abstract class AbstractSceneTargetDataProvider {

    protected static final int MARKET_KLINE_LIMIT = 250;
    private final ObjectMapper objectMapper;

    protected AbstractSceneTargetDataProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected ObjectMapper objectMapper() {
        return this.objectMapper;
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
        return SceneTargetDataConverter.message(
                taskNo,
                param,
                target,
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
                missing);
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
        return SceneTargetDataConverter.toMap(this.objectMapper, value);
    }

    protected List<Map<String, Object>> toMapList(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return SceneTargetDataConverter.toMapList(this.objectMapper, values);
    }

    protected Map<String, Object> compactMap(Map<String, Object> source) {
        return SceneTargetDataConverter.compactMap(source);
    }
}
