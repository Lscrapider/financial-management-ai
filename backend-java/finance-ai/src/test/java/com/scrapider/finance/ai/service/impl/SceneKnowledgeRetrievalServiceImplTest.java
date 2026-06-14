package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneChunkAllocationDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesTargetParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SceneKnowledgeRetrievalServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allocatesChunksAndBuildsRetrievalTasksUsingReportWeights() {
        SceneKnowledgeRetrievalServiceImpl service =
                new SceneKnowledgeRetrievalServiceImpl(new FakeKnowledgeVectorManage(Map.of()));

        List<SceneChunkAllocationDTO> allocations = service.allocateChunks(this.payload());
        List<SceneKnowledgeRetrievalTaskDTO> tasks = service.buildRetrievalTasks(allocations, this.payload());

        assertThat(allocations)
                .extracting(SceneChunkAllocationDTO::scene, SceneChunkAllocationDTO::chunkCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("risk_strategy", 3),
                        org.assertj.core.groups.Tuple.tuple("volume", 1),
                        org.assertj.core.groups.Tuple.tuple("valuation", 1),
                        org.assertj.core.groups.Tuple.tuple("price", 1));
        assertThat(tasks)
                .extracting(SceneKnowledgeRetrievalTaskDTO::scene, SceneKnowledgeRetrievalTaskDTO::chunkCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("risk_strategy", 3),
                        org.assertj.core.groups.Tuple.tuple("volume", 1),
                        org.assertj.core.groups.Tuple.tuple("valuation", 1),
                        org.assertj.core.groups.Tuple.tuple("price", 1));
        assertThat(tasks.get(0).queryText()).isEqualTo("风险策略查询");
        assertThat(tasks.get(0).currentTags())
                .containsExactly(
                        Map.entry("position_control", 0.9),
                        Map.entry("wait_confirm", 0.4));
    }

    @Test
    void retrievesRanksAndDeduplicatesKnowledgeChunksLikeReportPipeline() {
        FakeKnowledgeVectorManage knowledgeManage = new FakeKnowledgeVectorManage(Map.of(
                "valuation", this.valuationRows(),
                "risk_strategy", this.riskRows()));
        SceneKnowledgeRetrievalServiceImpl service = new SceneKnowledgeRetrievalServiceImpl(knowledgeManage);

        Map<String, List<SceneKnowledgeChunkDTO>> context = service.retrieveKnowledge(List.of(
                new SceneRetrievalEmbeddingParam(
                        "valuation",
                        1,
                        Map.of("low_pb", 0.8, "high_dividend", 0.6),
                        "估值查询",
                        this.embedding()),
                new SceneRetrievalEmbeddingParam(
                        "risk_strategy",
                        2,
                        Map.of("position_control", 0.9),
                        "风险策略查询",
                        this.embedding())));

        assertThat(knowledgeManage.queries)
                .extracting(Query::scene, Query::limit)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("valuation", 200),
                        org.assertj.core.groups.Tuple.tuple("risk_strategy", 200));
        assertThat(context.get("valuation"))
                .extracting(SceneKnowledgeChunkDTO::chunkId, SceneKnowledgeChunkDTO::matchedTags,
                        SceneKnowledgeChunkDTO::finalScore)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, List.of("low_pb", "high_dividend"), 0.775));
        assertThat(context.get("risk_strategy"))
                .extracting(SceneKnowledgeChunkDTO::chunkId, SceneKnowledgeChunkDTO::matchedTags,
                        SceneKnowledgeChunkDTO::finalScore)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(21L, List.of("position_control"), 0.82));
    }

    private SceneAnalysisCurrentScenesPayloadParam payload() {
        SceneAnalysisSceneModuleParam low = this.module(0.2, "低分查询", Map.of("quiet", 0.2));
        return new SceneAnalysisCurrentScenesPayloadParam(
                new SceneAnalysisCurrentScenesTargetParam("STOCK", "601318", "中国平安"),
                "risk_check",
                6,
                this.objectMapper.createObjectNode(),
                new SceneAnalysisCurrentScenesParam(
                        null,
                        this.module(0.4, "价格查询", Map.of("price_rise", 0.6)),
                        this.module(0.6, "成交量查询", Map.of("volume_expand", 0.7)),
                        low,
                        this.module(0.7, "估值查询", Map.of("low_pb", 0.8)),
                        low,
                        this.module(0.8, "风险策略查询", Map.of(
                                "position_control", 0.9,
                                "ignored", -0.1,
                                "wait_confirm", 0.4))));
    }

    private SceneAnalysisSceneModuleParam module(Double score, String queryText, Map<String, Double> tags) {
        return new SceneAnalysisSceneModuleParam(
                score,
                "high",
                "positive",
                tags,
                List.of(),
                queryText,
                Map.of());
    }

    private List<KnowledgeVectorSearchDTO> valuationRows() {
        return List.of(
                this.row(11L, "ocr-1", 1, "低估值不等于低风险", 0.7, Map.of(
                        "valuation", List.of("low_pb", "high_dividend"),
                        "risk_strategy", List.of("position_control"))),
                this.row(12L, "ocr-2", 1, "只命中低市净率", 0.9, Map.of(
                        "valuation", List.of("low_pb"))));
    }

    private List<KnowledgeVectorSearchDTO> riskRows() {
        return List.of(
                this.row(11L, "ocr-1", 1, "重复 chunk 应被去重", 0.95, Map.of(
                        "valuation", List.of("low_pb", "high_dividend"),
                        "risk_strategy", List.of("position_control"))),
                this.row(21L, "ocr-3", 1, "仓位控制需要等待确认", 0.8, Map.of(
                        "valuation", List.of("high_dividend"),
                        "risk_strategy", List.of("position_control"))),
                this.row(22L, "ocr-4", 1, "未命中当前风险标签", 0.99, Map.of(
                        "risk_strategy", List.of("wait_confirm"))));
    }

    private KnowledgeVectorSearchDTO row(
            Long id,
            String taskNo,
            Integer chunkIndex,
            String text,
            Double semanticScore,
            Map<String, List<String>> scenes) {
        KnowledgeVectorSearchDTO row = new KnowledgeVectorSearchDTO();
        row.setId(id);
        row.setTaskNo(taskNo);
        row.setChunkIndex(chunkIndex);
        row.setText(text);
        row.setSemanticScore(semanticScore);
        row.setMetadata(this.objectMapper.valueToTree(Map.of("scenes", scenes)));
        return row;
    }

    private List<Double> embedding() {
        return IntStream.range(0, 768).mapToObj(index -> 0.1D).toList();
    }

    private static class FakeKnowledgeVectorManage extends KnowledgeVectorManage {
        private final Map<String, List<KnowledgeVectorSearchDTO>> rowsByScene;
        private final List<Query> queries = new ArrayList<>();

        FakeKnowledgeVectorManage(Map<String, List<KnowledgeVectorSearchDTO>> rowsByScene) {
            this.rowsByScene = rowsByScene;
        }

        @Override
        public List<KnowledgeVectorSearchDTO> searchBySemantic(String scene, String queryEmbedding, int limit) {
            this.queries.add(new Query(scene, limit));
            return this.rowsByScene.getOrDefault(scene, List.of());
        }
    }

    private record Query(String scene, int limit) {
    }
}
