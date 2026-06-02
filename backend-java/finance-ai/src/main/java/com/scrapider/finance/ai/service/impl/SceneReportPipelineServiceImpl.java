package com.scrapider.finance.ai.service.impl;

import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.ai.service.SceneReportPipelineService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SceneReportPipelineServiceImpl implements SceneReportPipelineService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SceneReportPipelineServiceImpl.class);
    private static final List<String> RETRIEVAL_CATEGORIES =
            List.of("price", "volume", "trend", "valuation", "sentiment", "risk_strategy");
    private static final double ALPHA = 6.0;
    private static final double CATEGORY_SCORE_THRESHOLD = 0.35;
    private static final int MIN_PER_ACTIVE_CATEGORY = 1;
    private static final int MAX_PER_CATEGORY = 4;
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

    @Override
    public void start(String taskNo, SceneAnalysisCurrentScenesPayloadParam currentScenesPayload) {
        List<SceneChunkAllocationDTO> allocations = this.allocateChunks(currentScenesPayload);
        LOGGER.info("scene report chunk allocation calculated task_no={} allocations={}", taskNo, allocations);
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
        this.assignInitialCounts(candidates, totalChunks);
        this.rebalance(candidates, totalChunks);
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt(Candidate::chunkCount)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::effectiveScore).reversed()))
                .map(candidate -> new SceneChunkAllocationDTO(
                        candidate.category(),
                        candidate.chunkCount(),
                        candidate.score(),
                        candidate.effectiveScore()))
                .toList();
    }

    private List<Candidate> activeCandidates(SceneAnalysisCurrentScenesParam currentScenes, String reportType) {
        List<Candidate> candidates = new ArrayList<>();
        for (String category : RETRIEVAL_CATEGORIES) {
            SceneAnalysisSceneModuleParam module = currentScenes.module(category);
            if (module == null || module.score() == null || module.score() < CATEGORY_SCORE_THRESHOLD) {
                continue;
            }
            double score = module.score();
            double effectiveScore = score * this.reportTypeWeight(reportType, category);
            candidates.add(new Candidate(category, score, effectiveScore, 0));
        }
        return candidates;
    }

    private double reportTypeWeight(String reportType, String category) {
        String normalized = reportType == null ? "quick_analysis" : reportType.trim().toLowerCase(Locale.ROOT);
        Map<String, Double> weights = REPORT_TYPE_WEIGHTS.getOrDefault(
                normalized,
                REPORT_TYPE_WEIGHTS.get("quick_analysis"));
        return weights.getOrDefault(category, 1.0);
    }

    private void assignInitialCounts(List<Candidate> candidates, int totalChunks) {
        double allocationScoreSum = candidates.stream()
                .mapToDouble(candidate -> Math.exp(candidate.effectiveScore() * ALPHA))
                .sum();
        for (Candidate candidate : candidates) {
            double allocationScore = Math.exp(candidate.effectiveScore() * ALPHA);
            int chunkCount = (int) Math.round(allocationScore / allocationScoreSum * totalChunks);
            candidate.setChunkCount(this.clamp(chunkCount, MIN_PER_ACTIVE_CATEGORY, MAX_PER_CATEGORY));
        }
    }

    private void rebalance(List<Candidate> candidates, int totalChunks) {
        int currentTotal = candidates.stream().mapToInt(Candidate::chunkCount).sum();
        while (currentTotal > totalChunks) {
            Candidate candidate = candidates.stream()
                    .filter(item -> item.chunkCount() > MIN_PER_ACTIVE_CATEGORY)
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
                    .filter(item -> item.chunkCount() < MAX_PER_CATEGORY)
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
        private final String category;
        private final double score;
        private final double effectiveScore;
        private int chunkCount;

        Candidate(String category, double score, double effectiveScore, int chunkCount) {
            this.category = category;
            this.score = score;
            this.effectiveScore = effectiveScore;
            this.chunkCount = chunkCount;
        }

        String category() {
            return this.category;
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
}
