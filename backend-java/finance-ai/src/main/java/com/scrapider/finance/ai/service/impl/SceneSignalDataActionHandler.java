package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SceneSignalDataActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "scene.signal_data";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_TOTAL_CHUNKS = 6;
    private static final int MAX_TOTAL_CHUNKS = 10;

    private final ObjectMapper objectMapper;
    private final List<SceneTargetDataProvider> targetDataProviders;

    public SceneSignalDataActionHandler(ObjectMapper objectMapper, List<SceneTargetDataProvider> targetDataProviders) {
        this.objectMapper = objectMapper;
        this.targetDataProviders = targetDataProviders;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在计算当前场景信号";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String targetType = this.normalizeTargetType(this.textParam(params, "targetType"));
        String targetCode = StrUtil.trim(this.textParam(params, "targetCode"));
        if (StrUtil.isBlank(targetType) || StrUtil.isBlank(targetCode)) {
            return this.error("TARGET_REQUIRED", "targetType 和 targetCode 不能为空");
        }
        SceneTargetDataProvider provider = this.targetDataProvider(targetType);
        SceneAnalysisSubmitParam submitParam = new SceneAnalysisSubmitParam(
                targetType,
                targetCode,
                this.textParam(params, "targetName"),
                StrUtil.blankToDefault(this.textParam(params, "reportType"), "quick_analysis"),
                this.totalChunks(params),
                this.intParam(params, "dailyKlineLimit"),
                this.intParam(params, "weeklyKlineLimit"),
                this.intParam(params, "monthlyKlineLimit"),
                StrUtil.blankToDefault(this.textParam(params, "configProfile"), "system_recommended"),
                null);
        SceneAnalysisMessageDTO message = provider.buildMessage(this.newTaskNo(), targetCode, submitParam);
        return new AgentDataGatewayResponseVO(
                ACTION,
                true,
                List.of(this.toMap(message)),
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "targetType", targetType,
                        "targetCode", targetCode,
                        "totalChunks", submitParam.totalChunks()),
                null);
    }

    private SceneTargetDataProvider targetDataProvider(String targetType) {
        return this.targetDataProviders.stream()
                .filter(provider -> provider.supports(targetType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported targetType: " + targetType));
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOCK" -> "STOCK";
            case "INDEX" -> "INDEX";
            case "CONVERTIBLE_BOND", "BOND" -> "CONVERTIBLE_BOND";
            default -> normalized;
        };
    }

    private int totalChunks(JsonNode params) {
        Integer value = this.intParam(params, "totalChunks");
        if (value == null) {
            return DEFAULT_TOTAL_CHUNKS;
        }
        return Math.max(1, Math.min(value, MAX_TOTAL_CHUNKS));
    }

    private Integer intParam(JsonNode params, String field) {
        if (params == null || params.isNull() || !params.has(field) || params.get(field).isNull()) {
            return null;
        }
        JsonNode node = params.get(field);
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual() && StrUtil.isNotBlank(node.asText())) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String textParam(JsonNode params, String field) {
        if (params == null || params.isNull() || !params.has(field) || params.get(field).isNull()) {
            return null;
        }
        String value = params.get(field).asText();
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof SceneAnalysisMessageDTO message) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskNo", message.taskNo());
            row.put("requestedAt", message.requestedAt() == null ? null : message.requestedAt().toString());
            row.put("reportType", message.reportType());
            row.put("totalChunks", message.totalChunks());
            row.put("target", message.target());
            row.put("config", message.config());
            row.put("marketData", message.marketData());
            row.put("valuationData", message.valuationData());
            row.put("industryData", message.industryData());
            row.put("valuationHistory", message.valuationHistory());
            row.put("financialIndicators", message.financialIndicators());
            row.put("dividendHistory", message.dividendHistory());
            row.put("dailyKlines", message.dailyKlines());
            row.put("weeklyKlines", message.weeklyKlines());
            row.put("monthlyKlines", message.monthlyKlines());
            row.put("intradayData", message.intradayData());
            row.put("assetSpecificData", message.assetSpecificData());
            row.put("dataCompleteness", message.dataCompleteness());
            return row;
        }
        return this.objectMapper.convertValue(value, MAP_TYPE);
    }

    private String newTaskNo() {
        return "agent-scene-signal-" + UUID.randomUUID().toString().replace("-", "");
    }

    private AgentDataGatewayResponseVO error(String code, String message) {
        return new AgentDataGatewayResponseVO(
                ACTION,
                false,
                List.of(),
                Map.of("queriedAt", OffsetDateTime.now().toString()),
                new AgentDataGatewayResponseVO.Error(code, message));
    }
}
