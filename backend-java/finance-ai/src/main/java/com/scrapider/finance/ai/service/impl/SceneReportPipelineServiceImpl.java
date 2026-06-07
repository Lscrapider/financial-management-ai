package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.converter.SceneReportPipelineConverter;
import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.service.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.service.SceneAnalysisReportGenerationService;
import com.scrapider.finance.ai.service.SceneReportPipelineService;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
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
    private final ObjectMapper objectMapper;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final SceneAnalysisReportGenerationService sceneAnalysisReportGenerationService;

    public SceneReportPipelineServiceImpl(
            ObjectMapper objectMapper,
            KnowledgeVectorManage knowledgeVectorManage,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            SceneAnalysisReportGenerationService sceneAnalysisReportGenerationService) {
        this.objectMapper = objectMapper;
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.sceneAnalysisReportGenerationService = sceneAnalysisReportGenerationService;
    }

    @Override
    public void start(String taskNo, SceneAnalysisCurrentScenesPayloadParam currentScenesPayload) {
        this.sceneAnalysisTaskManage.markRetrievingKnowledge(taskNo);
        // 6.2 根据 7 大类得分计算 Chunk 分配比例。
        List<SceneChunkAllocationDTO> allocations = this.allocateChunks(currentScenesPayload);
        // 6.3 按 scene 生成检索任务。
        List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks =
                this.buildRetrievalTasks(allocations, currentScenesPayload);
        this.sceneAnalysisMessagePublisher.publishRetrievalEmbeddingMessage(
                SceneRetrievalEmbeddingMessageDTO.create(taskNo, retrievalTasks));
        LOGGER.info(
                "scene report retrieval embedding message published task_no={} allocations={} retrieval_tasks={}",
                taskNo,
                allocations,
                retrievalTasks.size());
    }

    @Override
    public void continueWithRetrievalEmbeddings(
            String taskNo,
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        if (retrievalEmbeddings == null || retrievalEmbeddings.isEmpty()) {
            throw new IllegalArgumentException("retrievalEmbeddings is required");
        }
        SceneAnalysisCurrentScenesPayloadParam currentScenesPayload = this.currentScenesPayload(taskNo);
        // 6.2 根据 7 大类得分计算 Chunk 分配比例。
        List<SceneChunkAllocationDTO> allocations = this.allocateChunks(currentScenesPayload);
        Map<String, List<SceneKnowledgeChunkDTO>> knowledgeContext =
                this.retrieveKnowledge(retrievalEmbeddings);
        // 6.9 构建 knowledgeContext 并写入 reportPayload，供后续报告生成使用。
        ObjectNode reportPayload = this.objectMapper.createObjectNode();
        reportPayload.set("chunkAllocation", this.objectMapper.valueToTree(allocations));
        reportPayload.set("retrievalTasks", this.objectMapper.valueToTree(this.retrievalTasks(retrievalEmbeddings)));
        reportPayload.set("knowledgeContext", this.objectMapper.valueToTree(knowledgeContext));
        this.sceneAnalysisTaskManage.saveKnowledgeContextPayload(taskNo, reportPayload);
        this.sceneAnalysisReportGenerationService.generateAfterKnowledgeRetrieved(taskNo);
        LOGGER.info(
                "scene report knowledge context calculated task_no={} allocations={} retrieval_embeddings={} scenes={}",
                taskNo,
                allocations,
                retrievalEmbeddings.size(),
                knowledgeContext.keySet());
    }

    private SceneAnalysisCurrentScenesPayloadParam currentScenesPayload(String taskNo) {
        SceneAnalysisTaskPO task = this.sceneAnalysisTaskManage.findByTaskNo(taskNo);
        if (task == null || task.getCurrentScenesPayload() == null || task.getCurrentScenesPayload().isNull()) {
            throw new IllegalArgumentException("currentScenesPayload is not ready");
        }
        try {
            return this.objectMapper.treeToValue(
                    task.getCurrentScenesPayload(),
                    SceneAnalysisCurrentScenesPayloadParam.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse currentScenesPayload", ex);
        }
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
                .map(candidate -> SceneReportPipelineConverter.allocation(
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
                    return SceneReportPipelineConverter.retrievalTask(
                            allocation.scene(),
                            allocation.chunkCount(),
                            currentTags,
                            // 6.5 每类语义检索 Query 生成。
                            this.queryText(module));
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

    private String queryText(SceneAnalysisSceneModuleParam module) {
        if (module == null || module.queryText() == null || module.queryText().isBlank()) {
            throw new IllegalArgumentException("currentScenes module queryText is required");
        }
        return module.queryText().trim();
    }

    private List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks(
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        return retrievalEmbeddings.stream()
                .map(item -> SceneReportPipelineConverter.retrievalTask(
                        item.scene(),
                        item.chunkCount(),
                        this.safeTags(item.currentTags()),
                        item.queryText()))
                .toList();
    }

    private Map<String, List<SceneKnowledgeChunkDTO>> retrieveKnowledge(
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        if (retrievalEmbeddings.isEmpty()) {
            return Map.of();
        }
        Map<String, SceneRetrievalEmbeddingParam> taskLookup = retrievalEmbeddings.stream()
                .collect(Collectors.toMap(
                        SceneRetrievalEmbeddingParam::scene,
                        task -> task,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<SceneKnowledgeChunkDTO>> context = SceneReportPipelineConverter.knowledgeContext();
        Set<Long> selectedChunkIds = new java.util.LinkedHashSet<>();
        for (SceneRetrievalEmbeddingParam task : retrievalEmbeddings) {
            Map<String, Double> currentTags = this.safeTags(task.currentTags());
            // 6.5 queryText -> queryEmbedding：queryEmbedding 由 Python 使用入库同款模型生成后回调。
            String queryEmbedding = this.formatVector(task.queryEmbedding());
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
                    currentTags,
                    currentTags.isEmpty() ? 0 : JACCARD_THRESHOLD);
            if (ranked.isEmpty() && !currentTags.isEmpty()) {
                ranked = this.rankCandidates(
                        task,
                        taskLookup,
                        rows,
                        currentTags,
                        LOWERED_JACCARD_THRESHOLD);
            }
            if (ranked.isEmpty() && currentTags.size() > 1) {
                ranked = this.rankCandidates(
                        task,
                        taskLookup,
                        rows,
                        this.coreTags(currentTags),
                        JACCARD_THRESHOLD);
            }
            // 6.8 每个 scene 取 TopN：按 6.2 分配到的 chunkCount 截断当前 scene 候选。
            List<SceneKnowledgeChunkDTO> selected = ranked.stream()
                    .filter(chunk -> chunk.chunkId() != null && !selectedChunkIds.contains(chunk.chunkId()))
                    .limit(task.chunkCount())
                    .toList();
            selected.stream()
                    .map(SceneKnowledgeChunkDTO::chunkId)
                    .forEach(selectedChunkIds::add);
            SceneReportPipelineConverter.putSceneChunks(context, task.scene(), selected);
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

    private Map<String, Double> safeTags(Map<String, Double> currentTags) {
        return currentTags == null ? Map.of() : currentTags;
    }

    private List<SceneKnowledgeChunkDTO> rankCandidates(
            SceneRetrievalEmbeddingParam task,
            Map<String, SceneRetrievalEmbeddingParam> taskLookup,
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
                            SceneReportPipelineConverter.knowledgeChunk(
                                    row,
                                    task.scene(),
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
            Map<String, SceneRetrievalEmbeddingParam> taskLookup) {
        int hitCount = 0;
        for (Map.Entry<String, SceneRetrievalEmbeddingParam> entry : taskLookup.entrySet()) {
            Map<String, Double> currentTags = this.safeTags(entry.getValue().currentTags());
            if (entry.getKey().equals(currentScene) || currentTags.isEmpty()) {
                continue;
            }
            Set<String> chunkTagSet = Set.copyOf(this.chunkTags(row, entry.getKey()));
            if (chunkTagSet.stream().anyMatch(currentTags.keySet()::contains)) {
                hitCount++;
            }
        }
        return Math.min(0.3, hitCount * 0.1);
    }

    private String formatVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("retrieval queryEmbedding is required");
        }
        if (embedding.size() != EXPECTED_EMBEDDING_DIMENSION) {
            throw new IllegalStateException(
                    "retrieval queryEmbedding dimension must match knowledge_vector.embedding dimension: "
                            + EXPECTED_EMBEDDING_DIMENSION);
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(i));
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
