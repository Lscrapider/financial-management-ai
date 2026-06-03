package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesTargetParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.ai.service.SceneReportPipelineService;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SceneReportPipelineServiceImpl implements SceneReportPipelineService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SceneReportPipelineServiceImpl.class);
    private static final List<String> RETRIEVAL_SCENES =
            List.of("price", "volume", "trend", "valuation", "sentiment", "risk_strategy");
    private static final double ALPHA = 6.0;
    private static final double SCENE_SCORE_THRESHOLD = 0.35;
    private static final double JACCARD_THRESHOLD = 0.2;
    private static final double LOWERED_JACCARD_THRESHOLD = 0.1;
    private static final int SEMANTIC_CANDIDATE_LIMIT = 200;
    private static final int EXPECTED_EMBEDDING_DIMENSION = 512;
    private static final int MIN_PER_ACTIVE_SCENE = 1;
    private static final int MAX_PER_SCENE = 4;
    private static final double SEMANTIC_SCORE_WEIGHT = 0.45;
    private static final double TAG_MATCH_SCORE_WEIGHT = 0.45;
    private static final double CROSS_SCENE_SCORE_WEIGHT = 0.10;
    private static final Map<String, Map<String, Double>> REPORT_TYPE_WEIGHTS = Map.of(
            "quick_analysis", Map.of(
                    "price", 1.0,
                    "volume", 1.0,
                    "trend", 0.9,
                    "valuation", 0.8,
                    "sentiment", 0.9,
                    "risk_strategy", 1.0),
            "risk_check", Map.of(
                    "price", 0.9,
                    "volume", 1.0,
                    "trend", 0.8,
                    "valuation", 0.7,
                    "sentiment", 1.1,
                    "risk_strategy", 1.3),
            "valuation_report", Map.of(
                    "price", 0.7,
                    "volume", 0.7,
                    "trend", 0.7,
                    "valuation", 1.5,
                    "sentiment", 0.6,
                    "risk_strategy", 1.0));
    private static final Map<String, String> SCENE_QUERY_TERMS = Map.of(
            "price", "价格走势 突破 回调 支撑压力",
            "volume", "成交量 换手率 量价关系 成交持续性",
            "trend", "趋势延续 趋势反转 区间震荡 突破失败",
            "valuation", "估值水平 PE PB 股息率 估值修复 估值陷阱",
            "sentiment", "市场情绪 关注度 新闻政策 板块轮动",
            "risk_strategy", "风险控制 仓位管理 等待确认 止盈止损");
    private static final Map<String, String> TAG_QUERY_TERMS = Map.ofEntries(
            Map.entry("price_rise", "价格上涨"),
            Map.entry("price_drop", "价格下跌"),
            Map.entry("near_recent_high", "接近近期高位"),
            Map.entry("near_recent_low", "接近近期低位"),
            Map.entry("breakout", "价格突破"),
            Map.entry("pullback", "回调"),
            Map.entry("volume_expand", "放量"),
            Map.entry("volume_shrink", "缩量"),
            Map.entry("high_turnover", "高换手"),
            Map.entry("low_turnover", "低换手"),
            Map.entry("volume_price_confirm", "量价确认"),
            Map.entry("volume_price_divergence", "量价背离"),
            Map.entry("uptrend", "上升趋势"),
            Map.entry("downtrend", "下降趋势"),
            Map.entry("range_bound", "区间震荡"),
            Map.entry("trend_reversal", "趋势反转"),
            Map.entry("low_pe", "低 PE"),
            Map.entry("high_pe", "高 PE"),
            Map.entry("low_pb", "低 PB"),
            Map.entry("high_pb", "高 PB"),
            Map.entry("high_dividend", "高股息"),
            Map.entry("valuation_repair", "估值修复"),
            Map.entry("valuation_trap", "估值陷阱"),
            Map.entry("market_attention_rise", "市场关注度提升"),
            Map.entry("short_term_emotion", "短线情绪"),
            Map.entry("panic_selling", "恐慌抛售"),
            Map.entry("policy_driven", "政策驱动"),
            Map.entry("sector_rotation", "板块轮动"),
            Map.entry("chase_high_risk", "追高风险"),
            Map.entry("false_breakout_risk", "假突破风险"),
            Map.entry("drawdown_risk", "回撤风险"),
            Map.entry("risk_control", "风险控制"),
            Map.entry("position_control", "仓位管理"),
            Map.entry("wait_confirm", "等待确认"),
            Map.entry("take_profit_plan", "止盈计划"),
            Map.entry("stop_loss_plan", "止损计划"));

    private final ObjectMapper objectMapper;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public SceneReportPipelineServiceImpl(
            ObjectMapper objectMapper,
            KnowledgeVectorManage knowledgeVectorManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.objectMapper = objectMapper;
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public void start(String taskNo, SceneAnalysisCurrentScenesPayloadParam currentScenesPayload) {
        this.sceneAnalysisTaskManage.markRetrievingKnowledge(taskNo);
        // 6.2 根据 7 大类得分计算 Chunk 分配比例。
        List<SceneChunkAllocationDTO> allocations = this.allocateChunks(currentScenesPayload);
        // 6.3 按 scene 生成检索任务。
        List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks =
                this.buildRetrievalTasks(allocations, currentScenesPayload);
        Map<String, List<SceneKnowledgeChunkDTO>> knowledgeContext =
                this.retrieveKnowledge(retrievalTasks);
        // 6.9 构建 knowledgeContext 并写入 reportPayload，供后续报告生成使用。
        ObjectNode reportPayload = this.objectMapper.createObjectNode();
        reportPayload.set("chunkAllocation", this.objectMapper.valueToTree(allocations));
        reportPayload.set("retrievalTasks", this.objectMapper.valueToTree(retrievalTasks));
        reportPayload.set("knowledgeContext", this.objectMapper.valueToTree(knowledgeContext));
        this.sceneAnalysisTaskManage.saveKnowledgeContextPayload(taskNo, reportPayload);
        LOGGER.info(
                "scene report knowledge context calculated task_no={} allocations={} retrieval_tasks={} scenes={}",
                taskNo,
                allocations,
                retrievalTasks.size(),
                knowledgeContext.keySet());
    }

    public List<SceneChunkAllocationDTO> allocateChunks(SceneAnalysisCurrentScenesPayloadParam payload) {
        if (payload == null) {
            throw new IllegalArgumentException("currentScenesPayload is required");
        }
        int totalChunks = payload.totalChunks() == null ? 0 : payload.totalChunks();
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("currentScenesPayload.totalChunks must be greater than 0");
        }
        SceneAnalysisCurrentScenesParam currentScenes = payload.currentScenes();
        if (currentScenes == null) {
            throw new IllegalArgumentException("currentScenesPayload.currentScenes is required");
        }
        List<Candidate> candidates = this.activeCandidates(currentScenes, payload.reportType());
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparingDouble(Candidate::effectiveScore).reversed());
        if (totalChunks < candidates.size()) {
            candidates = new ArrayList<>(candidates.subList(0, totalChunks));
        }
        // 6.2 分配实现：按 effectiveScore 指数放大后初分配，再按 min/max 约束回平总数。
        this.assignInitialCounts(candidates, totalChunks);
        this.rebalance(candidates, totalChunks);
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt(Candidate::chunkCount)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::effectiveScore).reversed()))
                .map(candidate -> new SceneChunkAllocationDTO(
                        candidate.scene(),
                        candidate.chunkCount(),
                        candidate.score(),
                        candidate.effectiveScore()))
                .toList();
    }

    private List<Candidate> activeCandidates(SceneAnalysisCurrentScenesParam currentScenes, String reportType) {
        List<Candidate> candidates = new ArrayList<>();
        for (String scene : RETRIEVAL_SCENES) {
            SceneAnalysisSceneModuleParam module = currentScenes.module(scene);
            if (module == null || module.score() == null || module.score() < SCENE_SCORE_THRESHOLD) {
                continue;
            }
            double score = module.score();
            double effectiveScore = score * this.reportTypeWeight(reportType, scene);
            candidates.add(new Candidate(scene, score, effectiveScore, 0));
        }
        return candidates;
    }

    private double reportTypeWeight(String reportType, String scene) {
        String normalized = reportType == null ? "quick_analysis" : reportType.trim().toLowerCase(Locale.ROOT);
        Map<String, Double> weights = REPORT_TYPE_WEIGHTS.getOrDefault(
                normalized,
                REPORT_TYPE_WEIGHTS.get("quick_analysis"));
        return weights.getOrDefault(scene, 1.0);
    }

    private List<SceneKnowledgeRetrievalTaskDTO> buildRetrievalTasks(
            List<SceneChunkAllocationDTO> allocations,
            SceneAnalysisCurrentScenesPayloadParam payload) {
        if (allocations.isEmpty()) {
            return List.of();
        }
        return allocations.stream()
                .filter(allocation -> allocation.chunkCount() > 0)
                .map(allocation -> {
                    SceneAnalysisSceneModuleParam module =
                            payload.currentScenes().module(allocation.scene());
                    Map<String, Double> currentTags = this.currentTags(module);
                    return new SceneKnowledgeRetrievalTaskDTO(
                            allocation.scene(),
                            allocation.chunkCount(),
                            currentTags,
                            // 6.5 每类语义检索 Query 生成。
                            this.buildQueryText(allocation.scene(), currentTags, payload));
                })
                .toList();
    }

    private Map<String, Double> currentTags(SceneAnalysisSceneModuleParam module) {
        if (module == null || module.tags() == null || module.tags().isEmpty()) {
            return Map.of();
        }
        return module.tags().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        entry -> entry.getKey().trim(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String buildQueryText(
            String scene,
            Map<String, Double> currentTags,
            SceneAnalysisCurrentScenesPayloadParam payload) {
        List<String> terms = currentTags.keySet().stream()
                .map(tag -> TAG_QUERY_TERMS.getOrDefault(tag, tag))
                .collect(Collectors.toCollection(ArrayList::new));
        terms.add(SCENE_QUERY_TERMS.getOrDefault(scene, scene));
        SceneAnalysisCurrentScenesTargetParam target = payload.target();
        if (target != null && target.name() != null && !target.name().isBlank()) {
            terms.add(target.name().trim());
        }
        if (target != null && target.type() != null && !target.type().isBlank()) {
            terms.add(target.type().trim());
        }
        if (payload.reportType() != null && !payload.reportType().isBlank()) {
            terms.add(payload.reportType().trim());
        }
        return String.join(" ", terms);
    }

    private Map<String, List<SceneKnowledgeChunkDTO>> retrieveKnowledge(
            List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks) {
        if (retrievalTasks.isEmpty()) {
            return Map.of();
        }
        EmbeddingModel embeddingModel = this.embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel is required for scene knowledge retrieval");
        }
        Map<String, SceneKnowledgeRetrievalTaskDTO> taskLookup = retrievalTasks.stream()
                .collect(Collectors.toMap(
                        SceneKnowledgeRetrievalTaskDTO::scene,
                        task -> task,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<SceneKnowledgeChunkDTO>> context = new LinkedHashMap<>();
        for (SceneKnowledgeRetrievalTaskDTO task : retrievalTasks) {
            // 6.5 queryText -> queryEmbedding。
            String queryEmbedding = this.formatVector(embeddingModel.embed(task.queryText()));
            // 6.6 用 pgvector 生成当前 scene 候选集合和 semantic_score。
            List<KnowledgeVectorSearchDTO> rows = this.knowledgeVectorManage.searchBySemantic(
                    task.scene(),
                    queryEmbedding,
                    SEMANTIC_CANDIDATE_LIMIT);
            // 6.4 标签查询与候选过滤；6.7 候选 Chunk 综合评分与类内重排序。
            List<SceneKnowledgeChunkDTO> ranked = this.rankCandidates(
                    task,
                    taskLookup,
                    rows,
                    task.currentTags(),
                    task.currentTags().isEmpty() ? 0 : JACCARD_THRESHOLD);
            if (ranked.isEmpty() && !task.currentTags().isEmpty()) {
                ranked = this.rankCandidates(
                        task,
                        taskLookup,
                        rows,
                        task.currentTags(),
                        LOWERED_JACCARD_THRESHOLD);
            }
            if (ranked.isEmpty() && task.currentTags().size() > 1) {
                ranked = this.rankCandidates(
                        task,
                        taskLookup,
                        rows,
                        this.coreTags(task.currentTags()),
                        JACCARD_THRESHOLD);
            }
            // 6.8 每个 scene 取 TopN：按 6.2 分配到的 chunkCount 截断当前 scene 候选。
            context.put(task.scene(), ranked.stream()
                    .limit(task.chunkCount())
                    .toList());
        }
        return context;
    }

    private Map<String, Double> coreTags(Map<String, Double> currentTags) {
        return currentTags.entrySet().stream()
                .limit(1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private List<SceneKnowledgeChunkDTO> rankCandidates(
            SceneKnowledgeRetrievalTaskDTO task,
            Map<String, SceneKnowledgeRetrievalTaskDTO> taskLookup,
            List<KnowledgeVectorSearchDTO> rows,
            Map<String, Double> requiredTags,
            double jaccardThreshold) {
        Set<String> requiredTagSet = requiredTags.keySet();
        return rows.stream()
                .map(row -> {
                    List<String> chunkTags = this.chunkTags(row, task.scene());
                    // 6.4 Jaccard 标签匹配分，低于阈值的候选会被过滤。
                    double tagMatchScore = this.jaccard(requiredTagSet, Set.copyOf(chunkTags));
                    List<String> matchedTags = chunkTags.stream()
                            .filter(requiredTagSet::contains)
                            .toList();
                    // 6.7 计算 cross_scene_score，并合成 final_score 做类内重排序。
                    double crossSceneScore = this.crossSceneScore(row, task.scene(), taskLookup);
                    double semanticScore = row.getSemanticScore() == null ? 0 : row.getSemanticScore();
                    double finalScore = SEMANTIC_SCORE_WEIGHT * semanticScore
                            + TAG_MATCH_SCORE_WEIGHT * tagMatchScore
                            + CROSS_SCENE_SCORE_WEIGHT * crossSceneScore;
                    return new RankedChunk(
                            new SceneKnowledgeChunkDTO(
                                    row.getId(),
                                    row.getTaskNo(),
                                    row.getChunkIndex(),
                                    task.scene(),
                                    row.getText(),
                                    matchedTags,
                                    this.roundScore(semanticScore),
                                    this.roundScore(tagMatchScore),
                                    this.roundScore(crossSceneScore),
                                    this.roundScore(finalScore)),
                            tagMatchScore);
                })
                .filter(ranked -> ranked.tagMatchScore() >= jaccardThreshold)
                .sorted(Comparator
                        .comparingDouble((RankedChunk ranked) -> ranked.chunk().finalScore())
                        .reversed()
                        .thenComparing(ranked -> ranked.chunk().chunkId()))
                .map(RankedChunk::chunk)
                .toList();
    }

    private List<String> chunkTags(KnowledgeVectorSearchDTO row, String scene) {
        if (row.getMetadata() == null) {
            return List.of();
        }
        var tagNode = row.getMetadata().path("scenes").path(scene);
        if (!tagNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        tagNode.forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                tags.add(node.asText());
            }
        });
        return tags;
    }

    private double jaccard(Set<String> requiredTags, Set<String> chunkTags) {
        if (requiredTags.isEmpty()) {
            return 0;
        }
        Set<String> union = new java.util.HashSet<>(requiredTags);
        union.addAll(chunkTags);
        if (union.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new java.util.HashSet<>(requiredTags);
        intersection.retainAll(chunkTags);
        return (double) intersection.size() / union.size();
    }

    private double crossSceneScore(
            KnowledgeVectorSearchDTO row,
            String currentScene,
            Map<String, SceneKnowledgeRetrievalTaskDTO> taskLookup) {
        int hitCount = 0;
        for (Map.Entry<String, SceneKnowledgeRetrievalTaskDTO> entry : taskLookup.entrySet()) {
            if (entry.getKey().equals(currentScene) || entry.getValue().currentTags().isEmpty()) {
                continue;
            }
            Set<String> chunkTagSet = Set.copyOf(this.chunkTags(row, entry.getKey()));
            if (chunkTagSet.stream().anyMatch(entry.getValue().currentTags().keySet()::contains)) {
                hitCount++;
            }
        }
        return Math.min(0.3, hitCount * 0.1);
    }

    private String formatVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("EmbeddingModel returned empty vector");
        }
        if (embedding.length != EXPECTED_EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "EmbeddingModel vector dimension must match knowledge_vector.embedding dimension: "
                            + EXPECTED_EMBEDDING_DIMENSION);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private double roundScore(double score) {
        return Math.round(score * 1000000.0) / 1000000.0;
    }

    private void assignInitialCounts(List<Candidate> candidates, int totalChunks) {
        double allocationScoreSum = candidates.stream()
                .mapToDouble(candidate -> Math.exp(candidate.effectiveScore() * ALPHA))
                .sum();
        for (Candidate candidate : candidates) {
            double allocationScore = Math.exp(candidate.effectiveScore() * ALPHA);
            int chunkCount = (int) Math.round(allocationScore / allocationScoreSum * totalChunks);
            candidate.setChunkCount(this.clamp(chunkCount, MIN_PER_ACTIVE_SCENE, MAX_PER_SCENE));
        }
    }

    private void rebalance(List<Candidate> candidates, int totalChunks) {
        int currentTotal = candidates.stream().mapToInt(Candidate::chunkCount).sum();
        while (currentTotal > totalChunks) {
            Candidate candidate = candidates.stream()
                    .filter(item -> item.chunkCount() > MIN_PER_ACTIVE_SCENE)
                    .min(Comparator.comparingDouble(Candidate::effectiveScore))
                    .orElse(null);
            if (candidate == null) {
                break;
            }
            candidate.setChunkCount(candidate.chunkCount() - 1);
            currentTotal--;
        }
        while (currentTotal < totalChunks) {
            Candidate candidate = candidates.stream()
                    .filter(item -> item.chunkCount() < MAX_PER_SCENE)
                    .max(Comparator.comparingDouble(Candidate::effectiveScore))
                    .orElse(null);
            if (candidate == null) {
                break;
            }
            candidate.setChunkCount(candidate.chunkCount() + 1);
            currentTotal++;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Candidate {
        private final String scene;
        private final double score;
        private final double effectiveScore;
        private int chunkCount;

        Candidate(String scene, double score, double effectiveScore, int chunkCount) {
            this.scene = scene;
            this.score = score;
            this.effectiveScore = effectiveScore;
            this.chunkCount = chunkCount;
        }

        String scene() {
            return this.scene;
        }

        double score() {
            return this.score;
        }

        double effectiveScore() {
            return this.effectiveScore;
        }

        int chunkCount() {
            return this.chunkCount;
        }

        void setChunkCount(int chunkCount) {
            this.chunkCount = chunkCount;
        }
    }

    private record RankedChunk(SceneKnowledgeChunkDTO chunk, double tagMatchScore) {
    }
}
