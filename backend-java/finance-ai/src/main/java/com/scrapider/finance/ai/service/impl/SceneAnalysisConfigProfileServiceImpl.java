package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.param.SceneAnalysisConfigProfileParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigFieldVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigGroupVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigProfileVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTypeVO;
import com.scrapider.finance.ai.service.SceneAnalysisConfigProfileService;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import com.scrapider.finance.manage.SceneAnalysisConfigProfileManage;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisConfigProfileServiceImpl implements SceneAnalysisConfigProfileService {

    private static final int DEFAULT_TOTAL_CHUNKS = 10;
    private static final List<SceneAnalysisConfigGroupVO> PARAMETER_SCHEMA = List.of(
            group("asset", "标的", List.of(
                    field("lowPriceThreshold", "低价阈值", List.of("asset_config", "low_price_threshold"),
                            5.0, 1.0, 20.0, 0.5, "元", "3 到 10",
                            "判断低价标的的价格边界，主要影响资产特征相关标签。"))),
            group("price", "价格", List.of(
                    field("priceRiseCenter", "上涨中心", List.of("price_config", "price_rise_center"),
                            2.0, 0.5, 8.0, 0.1, "%", "1.5 到 4",
                            "涨幅达到该中心值附近时，更容易触发上涨相关场景。"),
                    field("priceDropCenter", "下跌中心", List.of("price_config", "price_drop_center"),
                            2.0, 0.5, 8.0, 0.1, "%", "1.5 到 4",
                            "跌幅达到该中心值附近时，更容易触发下跌相关场景。"),
                    field("pullbackThreshold", "回撤阈值", List.of("price_config", "pullback_threshold"),
                            0.08, 0.01, 0.3, 0.01, null, "0.05 到 0.12",
                            "用于识别从高点回撤的幅度阈值。"),
                    field("gapThreshold", "跳空阈值", List.of("price_config", "gap_threshold"),
                            0.03, 0.005, 0.12, 0.005, null, "0.02 到 0.05",
                            "用于识别跳空缺口，数值越小越敏感。"))),
            group("volume", "量能", List.of(
                    field("volumeExpandCenter", "放量中心", List.of("volume_config", "volume_expand_center"),
                            1.0, 0.2, 4.0, 0.1, null, "0.8 到 1.8",
                            "量能相对历史或行业分布的放大中心。"),
                    field("volumeSpikeCenter", "脉冲放量中心", List.of("volume_config", "volume_spike_center"),
                            1.8, 0.5, 6.0, 0.1, null, "1.5 到 3",
                            "极端放量识别中心，越低越容易识别为脉冲放量。"))),
            group("trend", "趋势", List.of(
                    field("reboundThreshold", "反弹阈值", List.of("trend_config", "rebound_threshold"),
                            0.05, 0.01, 0.2, 0.01, null, "0.03 到 0.08",
                            "从阶段低位反弹的幅度阈值。"),
                    field("breakoutBaseConfirm", "突破基础确认权重",
                            List.of("trend_config", "breakout_from_range_confirm_weights", "base_confirm"),
                            0.7, 0.0, 1.0, 0.05, null, "0.6 到 0.8",
                            "突破箱体时，基础突破信号在确认分中的权重。"),
                    field("breakoutVolumeExpand", "突破放量确认权重",
                            List.of("trend_config", "breakout_from_range_confirm_weights", "volume_expand"),
                            0.3, 0.0, 1.0, 0.05, null, "0.2 到 0.4",
                            "突破箱体时，量能放大信号在确认分中的权重。"))),
            group("sentiment", "情绪", List.of(
                    field("attentionRiseCenter", "关注升温中心",
                            List.of("sentiment_config", "attention_rise_center"),
                            1.5, 0.2, 4.0, 0.1, null, "1 到 2",
                            "市场关注度上升的中心值，影响情绪升温相关场景。"),
                    field("lowAttentionScale", "低关注敏感度",
                            List.of("sentiment_config", "low_attention_scale"),
                            0.5, 0.1, 1.5, 0.1, null, "0.3 到 0.8",
                            "低关注识别的缩放参数，越低越敏感。"),
                    field("emotionPriceRise", "情绪价格权重",
                            List.of("sentiment_config", "market_proxy_emotion_weights", "price_rise"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "情绪升温代理指标中，价格上涨信号的权重。"))),
            group("risk", "风险", List.of(
                    field("spreadThreshold", "价差风险阈值",
                            List.of("risk_strategy_config", "spread_threshold"),
                            0.01, 0.001, 0.08, 0.001, null, "0.005 到 0.02",
                            "买卖价差较宽时的流动性风险识别阈值。"),
                    field("supportDistanceThreshold", "支撑距离阈值",
                            List.of("risk_strategy_config", "support_distance_threshold"),
                            0.08, 0.01, 0.3, 0.01, null, "0.05 到 0.12",
                            "距离支撑位过远时，仓位和止损策略会更谨慎。"),
                    field("chaseHighPriceRise", "追高价格权重",
                            List.of("risk_strategy_config", "chase_high_risk_weights", "price_rise"),
                            0.25, 0.0, 1.0, 0.05, null, "0.2 到 0.35",
                            "追高风险中，价格上涨信号的权重。"),
                    field("falseBreakout", "假突破权重",
                            List.of("risk_strategy_config", "false_breakout_risk_weights", "breakout"),
                            0.35, 0.0, 1.0, 0.05, null, "0.25 到 0.45",
                            "假突破风险中，突破信号自身的权重。"))));

    private final SceneAnalysisConfigProfileManage sceneAnalysisConfigProfileManage;

    public SceneAnalysisConfigProfileServiceImpl(SceneAnalysisConfigProfileManage sceneAnalysisConfigProfileManage) {
        this.sceneAnalysisConfigProfileManage = sceneAnalysisConfigProfileManage;
    }

    @Override
    public List<SceneAnalysisConfigGroupVO> parameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public List<SceneAnalysisReportTypeVO> reportTypes() {
        return Arrays.stream(SceneAnalysisReportTypeEnum.values())
                .map(SceneAnalysisReportTypeVO::fromEnum)
                .toList();
    }

    @Override
    public List<SceneAnalysisConfigProfileVO> listProfiles() {
        Long userId = this.currentUserId();
        return this.sceneAnalysisConfigProfileManage.listAvailable(userId).stream()
                .map(SceneAnalysisConfigProfileVO::fromPO)
                .toList();
    }

    @Override
    public SceneAnalysisConfigProfileVO create(SceneAnalysisConfigProfileParam param) {
        Long userId = this.currentUserId();
        NormalizedProfile normalized = this.normalized(param, this.generatedConfigProfile(userId));
        SceneAnalysisConfigProfilePO profile = SceneAnalysisConfigProfilePO.createCustom(
                userId,
                normalized.name(),
                normalized.configGroup(),
                normalized.configProfile(),
                normalized.targetType(),
                normalized.reportType(),
                normalized.configJson());
        this.sceneAnalysisConfigProfileManage.createProfile(profile);
        return SceneAnalysisConfigProfileVO.fromPO(profile);
    }

    @Override
    public SceneAnalysisConfigProfileVO update(Long id, SceneAnalysisConfigProfileParam param) {
        if (id == null) {
            throw new IllegalArgumentException("config profile id is required");
        }
        Long userId = this.currentUserId();
        SceneAnalysisConfigProfilePO existing = this.sceneAnalysisConfigProfileManage.getAvailable(id, userId);
        if (existing == null || Boolean.TRUE.equals(existing.getSystemDefault())) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        NormalizedProfile normalized = this.normalized(param, existing.getConfigProfile());
        existing.setName(normalized.name());
        existing.setConfigGroup(normalized.configGroup());
        existing.setConfigProfile(normalized.configProfile());
        existing.setTargetType(normalized.targetType());
        existing.setReportType(normalized.reportType());
        existing.setConfigJson(normalized.configJson());
        if (!this.sceneAnalysisConfigProfileManage.updateEditable(existing)) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        return SceneAnalysisConfigProfileVO.fromPO(existing);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("config profile id is required");
        }
        Long userId = this.currentUserId();
        SceneAnalysisConfigProfilePO existing = this.sceneAnalysisConfigProfileManage.getById(id);
        if (existing == null) {
            return;
        }
        if (Boolean.TRUE.equals(existing.getSystemDefault())
                || existing.getUserId() == null
                || !Objects.equals(existing.getUserId(), userId)) {
            throw new IllegalArgumentException("config profile is not editable");
        }
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            return;
        }
        if (!this.sceneAnalysisConfigProfileManage.disableEditable(id, userId)) {
            SceneAnalysisConfigProfilePO latest = this.sceneAnalysisConfigProfileManage.getById(id);
            if (latest != null && !Boolean.TRUE.equals(latest.getEnabled())) {
                return;
            }
            throw new IllegalArgumentException("config profile is not editable");
        }
    }

    private NormalizedProfile normalized(SceneAnalysisConfigProfileParam param, String configProfile) {
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String name = StrUtil.trim(param.name());
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("config profile name is required");
        }
        JsonNode configJson = this.configJson(param.configJson());
        String reportType = this.normalizeReportType(this.firstNotBlank(
                param.reportType(),
                configJson.path("reportType").asText(null),
                SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode()));
        String targetType = this.normalizeTargetType(this.firstNotBlank(
                param.targetType(),
                configJson.path("targetType").asText(null)));
        String configGroup = StrUtil.blankToDefault(StrUtil.trim(param.configGroup()), "自定义");

        ObjectNode normalizedJson = configJson.deepCopy();
        normalizedJson.put("reportType", reportType);
        normalizedJson.put("totalChunks", this.totalChunks(configJson));
        normalizedJson.put("configProfile", configProfile);
        if (StrUtil.isNotBlank(targetType)) {
            normalizedJson.put("targetType", targetType);
        } else {
            normalizedJson.remove("targetType");
        }
        if (!normalizedJson.has("userOverrides") || normalizedJson.path("userOverrides").isNull()) {
            normalizedJson.set("userOverrides", JsonNodeFactory.instance.objectNode());
        }
        if (!normalizedJson.path("userOverrides").isObject()) {
            throw new IllegalArgumentException("userOverrides must be an object");
        }
        return new NormalizedProfile(
                name,
                configGroup,
                configProfile,
                targetType,
                reportType,
                normalizedJson);
    }

    private String normalizeReportType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return SceneAnalysisReportTypeEnum.QUICK_ANALYSIS.getCode();
        }
        return SceneAnalysisReportTypeEnum.of(reportType.trim()).getCode();
    }

    private JsonNode configJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("configJson must be an object");
        }
        return node;
    }

    private int totalChunks(JsonNode configJson) {
        int totalChunks = configJson.path("totalChunks").asInt(DEFAULT_TOTAL_CHUNKS);
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("totalChunks must be greater than 0");
        }
        return totalChunks;
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
            default -> throw new IllegalArgumentException("unsupported targetType: " + targetType);
        };
    }

    private String generatedConfigProfile(Long userId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "user_" + userId + "_" + suffix;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("login required");
        }
        Object principal = authentication.getPrincipal();
        try {
            Method getUser = principal.getClass().getMethod("getUser");
            Object user = getUser.invoke(principal);
            Method getId = user.getClass().getMethod("getId");
            Object id = getId.invoke(user);
            if (id instanceof Long userId) {
                return userId;
            }
            if (id instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("login user id is unavailable", ex);
        }
        throw new IllegalArgumentException("login user id is unavailable");
    }

    private record NormalizedProfile(
            String name,
            String configGroup,
            String configProfile,
            String targetType,
            String reportType,
            ObjectNode configJson) {
    }

    private static SceneAnalysisConfigGroupVO group(
            String name,
            String label,
            List<SceneAnalysisConfigFieldVO> fields) {
        return new SceneAnalysisConfigGroupVO(name, label, fields);
    }

    private static SceneAnalysisConfigFieldVO field(
            String key,
            String label,
            List<String> path,
            Double defaultValue,
            Double min,
            Double max,
            Double step,
            String unit,
            String recommended,
            String description) {
        return new SceneAnalysisConfigFieldVO(
                key,
                label,
                path,
                defaultValue,
                min,
                max,
                step,
                unit,
                recommended,
                description);
    }
}
