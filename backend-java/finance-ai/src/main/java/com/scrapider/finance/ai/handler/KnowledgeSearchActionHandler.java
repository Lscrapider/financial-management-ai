package com.scrapider.finance.ai.handler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrTaskManage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSearchActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "knowledge.search";

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 8;
    private static final int SEMANTIC_CANDIDATE_LIMIT = 200;
    private static final int EXPECTED_EMBEDDING_DIMENSION = 512;
    private static final double SEMANTIC_SCORE_WEIGHT = 0.45;
    private static final double TAG_MATCH_SCORE_WEIGHT = 0.45;
    private static final double CROSS_SCENE_SCORE_WEIGHT = 0.10;
    private static final Map<String, Set<String>> VALID_TAGS = Map.of(
            "asset", Set.of("general", "stock", "index", "convertible_bond", "fund",
                    "etf", "lof", "index_fund", "active_fund", "bond_fund", "money_fund",
                    "qdii_fund", "bank_stock", "low_price_stock", "large_cap_stock", "small_cap_stock"),
            "price", Set.of("price_rise", "price_drop", "sideways", "near_recent_high",
                    "near_recent_low", "breakout", "pullback", "gap_up", "gap_down",
                    "convertible_high_price_risk", "convertible_low_price_defensive"),
            "volume", Set.of("volume_expand", "volume_shrink", "high_turnover",
                    "low_turnover", "volume_price_confirm", "volume_price_divergence",
                    "volume_spike", "volume_dry_up"),
            "trend", Set.of("uptrend", "downtrend", "range_bound", "rebound",
                    "pullback", "repair", "trend_reversal", "breakout_from_range",
                    "breakdown_from_range", "continuation", "turn_weak", "turn_strong",
                    "failed_breakout"),
            "valuation", Set.of("low_pe", "high_pe", "low_pb", "high_pb",
                    "high_dividend", "valuation_repair", "valuation_trap", "fundamental_risk",
                    "convertible_low_premium", "convertible_high_premium",
                    "convertible_premium_compression", "convertible_premium_expansion",
                    "convertible_debt_floor_support", "convertible_high_ytm",
                    "convertible_low_ytm", "convertible_high_conversion_value"),
            "sentiment", Set.of("market_attention_rise", "short_term_emotion",
                    "panic_selling", "news_driven", "policy_driven", "sector_rotation",
                    "weak_sentiment", "herding_effect", "institutional_behavior",
                    "convertible_stock_linkage", "convertible_independent_strength"),
            "risk_strategy", Set.of("chase_high_risk", "false_breakout_risk",
                    "liquidity_risk", "drawdown_risk", "valuation_trap_risk",
                    "overheated_risk", "risk_control", "position_control", "wait_confirm",
                    "observe_next_day", "avoid_emotional_trade", "take_profit_plan",
                    "stop_loss_plan", "convertible_forced_redeem_risk",
                    "convertible_putback_risk", "convertible_low_rating_risk",
                    "convertible_small_balance_risk", "convertible_liquidity_risk"));

    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrTaskManage ocrTaskManage;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchActionHandler(
            KnowledgeVectorManage knowledgeVectorManage,
            OcrTaskManage ocrTaskManage,
            ObjectMapper objectMapper) {
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.ocrTaskManage = ocrTaskManage;
        this.objectMapper = objectMapper;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在检索知识库依据";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        String queryText = this.textParam(params, "queryText");
        if (StrUtil.isBlank(queryText)) {
            return this.error("QUERY_TEXT_REQUIRED", "queryText 不能为空");
        }
        List<Double> queryEmbeddingValues = this.queryEmbeddingValues(params);
        if (queryEmbeddingValues.isEmpty()) {
            return this.error("QUERY_EMBEDDING_REQUIRED", "queryEmbedding 不能为空");
        }
        if (queryEmbeddingValues.size() != EXPECTED_EMBEDDING_DIMENSION) {
            return this.error("QUERY_EMBEDDING_DIMENSION_INVALID", "queryEmbedding 维度必须为 512");
        }
        String queryEmbedding = this.serializeQueryEmbedding(queryEmbeddingValues);
        int limit = this.limit(param);
        Map<String, Set<String>> queryTags = this.queryTags(params);
        List<String> scenes = this.scenes(params, queryTags.keySet());
        List<KnowledgeVectorSearchDTO> candidates = this.knowledgeVectorManage.searchBySemantic(
                scenes,
                queryEmbedding,
                SEMANTIC_CANDIDATE_LIMIT);
        Map<String, String> filenames = this.filenameMap(candidates);
        List<Map<String, Object>> rows = candidates.stream()
                .map(row -> this.scoredRow(row, queryTags, filenames))
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> row) -> this.doubleValue(row.get("finalScore")))
                        .reversed())
                .limit(limit)
                .toList();
        return new AgentDataGatewayResponseVO(
                ACTION,
                true,
                rows,
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "queryText", queryText,
                        "scenes", scenes,
                        "limit", limit),
                null);
    }

    private Map<String, Object> scoredRow(
            KnowledgeVectorSearchDTO row,
            Map<String, Set<String>> queryTags,
            Map<String, String> filenames) {
        double semanticScore = row.getSemanticScore() == null ? 0.0 : row.getSemanticScore();
        Map<String, List<String>> chunkTags = this.chunkTags(row.getMetadata());
        List<String> matchedTags = this.matchedTags(queryTags, chunkTags);
        double tagMatchScore = this.tagMatchScore(queryTags, chunkTags);
        double crossSceneScore = this.crossSceneScore(queryTags, chunkTags);
        double finalScore = this.roundScore(SEMANTIC_SCORE_WEIGHT * semanticScore
                + TAG_MATCH_SCORE_WEIGHT * tagMatchScore
                + CROSS_SCENE_SCORE_WEIGHT * crossSceneScore);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", filenames.getOrDefault(row.getTaskNo(), row.getTaskNo()));
        result.put("content", row.getText());
        result.put("chunkId", row.getId());
        result.put("taskNo", row.getTaskNo());
        result.put("chunkIndex", row.getChunkIndex());
        result.put("matchedTags", matchedTags);
        result.put("semanticScore", this.roundScore(semanticScore));
        result.put("tagMatchScore", this.roundScore(tagMatchScore));
        result.put("crossSceneScore", this.roundScore(crossSceneScore));
        result.put("finalScore", finalScore);
        return result;
    }

    private Map<String, List<String>> chunkTags(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return Map.of();
        }
        JsonNode scenes = metadata.path("scenes");
        if (!scenes.isObject()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        scenes.properties().forEach(entry -> {
            if (!entry.getValue().isArray()) {
                return;
            }
            List<String> tags = new ArrayList<>();
            entry.getValue().forEach(node -> {
                if (node.isTextual() && StrUtil.isNotBlank(node.asText())) {
                    tags.add(node.asText());
                }
            });
            result.put(entry.getKey(), tags);
        });
        return result;
    }

    private List<String> matchedTags(Map<String, Set<String>> queryTags, Map<String, List<String>> chunkTags) {
        List<String> matched = new ArrayList<>();
        queryTags.forEach((scene, tags) -> {
            Set<String> chunkSceneTags = Set.copyOf(chunkTags.getOrDefault(scene, List.of()));
            tags.stream()
                    .filter(chunkSceneTags::contains)
                    .forEach(matched::add);
        });
        return matched;
    }

    private double tagMatchScore(Map<String, Set<String>> queryTags, Map<String, List<String>> chunkTags) {
        if (queryTags.isEmpty()) {
            return 0.0;
        }
        double scoreSum = 0.0;
        int sceneCount = 0;
        for (Map.Entry<String, Set<String>> entry : queryTags.entrySet()) {
            Set<String> expected = entry.getValue();
            if (expected.isEmpty()) {
                continue;
            }
            Set<String> actual = Set.copyOf(chunkTags.getOrDefault(entry.getKey(), List.of()));
            Set<String> union = new LinkedHashSet<>(expected);
            union.addAll(actual);
            if (union.isEmpty()) {
                continue;
            }
            Set<String> intersection = new LinkedHashSet<>(expected);
            intersection.retainAll(actual);
            scoreSum += (double) intersection.size() / union.size();
            sceneCount++;
        }
        return sceneCount == 0 ? 0.0 : scoreSum / sceneCount;
    }

    private double crossSceneScore(Map<String, Set<String>> queryTags, Map<String, List<String>> chunkTags) {
        int hitSceneCount = 0;
        for (Map.Entry<String, Set<String>> entry : queryTags.entrySet()) {
            Set<String> expected = entry.getValue();
            if (expected.isEmpty()) {
                continue;
            }
            Set<String> actual = Set.copyOf(chunkTags.getOrDefault(entry.getKey(), List.of()));
            if (actual.stream().anyMatch(expected::contains)) {
                hitSceneCount++;
            }
        }
        return Math.min(0.3, Math.max(0, hitSceneCount - 1) * 0.1);
    }

    private Map<String, String> filenameMap(List<KnowledgeVectorSearchDTO> rows) {
        Set<String> taskNos = rows.stream()
                .map(KnowledgeVectorSearchDTO::getTaskNo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        return this.ocrTaskManage.listByTaskNos(taskNos).stream()
                .collect(Collectors.toMap(
                        OcrTaskPO::getTaskNo,
                        OcrTaskPO::getOriginalFilename,
                        (left, right) -> left));
    }

    private List<String> scenes(JsonNode params, Collection<String> tagScenes) {
        List<String> scenes = this.textList(params, "scenes").stream()
                .filter(VALID_TAGS::containsKey)
                .distinct()
                .toList();
        if (!scenes.isEmpty()) {
            return scenes;
        }
        return tagScenes.stream().filter(VALID_TAGS::containsKey).distinct().toList();
    }

    private Map<String, Set<String>> queryTags(JsonNode params) {
        JsonNode node = params == null ? null : params.path("tags");
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            Set<String> allowed = VALID_TAGS.get(entry.getKey());
            if (allowed == null || !entry.getValue().isArray()) {
                return;
            }
            Set<String> tags = new LinkedHashSet<>();
            entry.getValue().forEach(item -> {
                if (item.isTextual() && allowed.contains(item.asText())) {
                    tags.add(item.asText());
                }
            });
            if (!tags.isEmpty()) {
                result.put(entry.getKey(), tags);
            }
        });
        return result;
    }

    private List<Double> queryEmbeddingValues(JsonNode params) {
        JsonNode node = params == null ? null : params.path("queryEmbedding");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isNumber()) {
                values.add(item.asDouble());
            }
        });
        return values;
    }

    private String serializeQueryEmbedding(List<Double> values) {
        try {
            return this.objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("queryEmbedding 序列化失败", ex);
        }
    }

    private List<String> textList(JsonNode params, String field) {
        JsonNode node = params == null ? null : params.path(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && StrUtil.isNotBlank(item.asText())) {
                values.add(item.asText().trim());
            }
        });
        return values;
    }

    private int limit(AgentDataQueryParam param) {
        Integer paramsLimit = this.intParam(param.params(), "limit");
        Integer value = paramsLimit == null ? param.limit() : paramsLimit;
        if (value == null || value <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private Integer intParam(JsonNode params, String field) {
        if (params == null || !params.has(field) || params.get(field).isNull()) {
            return null;
        }
        JsonNode node = params.get(field);
        if (node.isNumber()) {
            return node.asInt();
        }
        return null;
    }

    private String textParam(JsonNode params, String field) {
        if (params == null || !params.has(field) || params.get(field).isNull()) {
            return null;
        }
        String value = params.get(field).asText();
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double roundScore(double score) {
        return Math.round(score * 1000000.0) / 1000000.0;
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
