package com.scrapider.finance.ai.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrTaskManage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KnowledgeSearchActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void capsLimitAndReturnsRankedRowsWithInternalFields() {
        FakeKnowledgeVectorManage knowledgeManage = new FakeKnowledgeVectorManage(this.rows());
        KnowledgeSearchActionHandler handler = new KnowledgeSearchActionHandler(
                knowledgeManage,
                new FakeOcrTaskManage(),
                this.objectMapper);
        AgentDataQueryParam param = new AgentDataQueryParam(
                "knowledge.search",
                this.objectMapper.valueToTree(Map.of(
                        "queryText", "低PB高股息银行股是否是价值陷阱",
                        "queryEmbedding", this.embedding(),
                        "scenes", List.of("valuation", "risk_strategy"),
                        "tags", Map.of(
                                "valuation", List.of("low_pb", "invalid_tag"),
                                "risk_strategy", List.of("position_control")),
                        "limit", 20)),
                20);

        AgentDataGatewayResponseVO response = handler.handle(this.session(), param);

        assertThat(response.success()).isTrue();
        assertThat(knowledgeManage.receivedLimit).isEqualTo(200);
        assertThat(response.metadata()).containsEntry("limit", 8);
        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0)).containsEntry("filename", "低估值投资方法.pdf");
        assertThat(response.data().get(0)).containsEntry("content", "低估值不等于低风险");
        assertThat(response.data().get(0)).containsKeys(
                "chunkId", "taskNo", "semanticScore", "tagMatchScore", "crossSceneScore", "finalScore");
        assertThat(response.data().get(0)).containsEntry("tagMatchScore", 0.75);
        assertThat(response.data().get(0)).containsEntry("crossSceneScore", 0.1);
        assertThat(response.data().get(0)).containsEntry("finalScore", 0.6625);
    }

    @Test
    void returnsErrorWhenQueryTextMissing() {
        KnowledgeSearchActionHandler handler = new KnowledgeSearchActionHandler(
                new FakeKnowledgeVectorManage(List.of()),
                new FakeOcrTaskManage(),
                this.objectMapper);
        AgentDataQueryParam param = new AgentDataQueryParam(
                "knowledge.search",
                this.objectMapper.valueToTree(Map.of("queryEmbedding", List.of(0.1))),
                null);

        AgentDataGatewayResponseVO response = handler.handle(this.session(), param);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("QUERY_TEXT_REQUIRED");
    }

    @Test
    void returnsErrorWhenEmbeddingDimensionInvalid() {
        KnowledgeSearchActionHandler handler = new KnowledgeSearchActionHandler(
                new FakeKnowledgeVectorManage(List.of()),
                new FakeOcrTaskManage(),
                this.objectMapper);
        AgentDataQueryParam param = new AgentDataQueryParam(
                "knowledge.search",
                this.objectMapper.valueToTree(Map.of(
                        "queryText", "低PB高股息银行股是否是价值陷阱",
                        "queryEmbedding", List.of(0.1))),
                null);

        AgentDataGatewayResponseVO response = handler.handle(this.session(), param);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("QUERY_EMBEDDING_DIMENSION_INVALID");
    }

    private List<KnowledgeVectorSearchDTO> rows() {
        KnowledgeVectorSearchDTO lowPb = new KnowledgeVectorSearchDTO();
        lowPb.setId(11L);
        lowPb.setTaskNo("ocr-1");
        lowPb.setChunkIndex(1);
        lowPb.setText("低估值不等于低风险");
        lowPb.setSemanticScore(0.7);
        lowPb.setMetadata(this.objectMapper.valueToTree(Map.of(
                "scenes", Map.of(
                        "valuation", List.of("low_pb", "high_dividend"),
                        "risk_strategy", List.of("position_control")),
                "chunkId", "ocr-1:chunk:0001")));

        KnowledgeVectorSearchDTO highPb = new KnowledgeVectorSearchDTO();
        highPb.setId(12L);
        highPb.setTaskNo("ocr-2");
        highPb.setChunkIndex(1);
        highPb.setText("高估值需要控制回撤");
        highPb.setSemanticScore(0.9);
        highPb.setMetadata(this.objectMapper.valueToTree(Map.of(
                "scenes", Map.of("valuation", List.of("high_pb")),
                "chunkId", "ocr-2:chunk:0001")));
        return List.of(lowPb, highPb);
    }

    private AgentSessionDTO session() {
        return new AgentSessionDTO(
                "agent-session",
                "secret",
                1L,
                "tester",
                "conversation",
                "message",
                Set.of("knowledge.search"),
                Instant.now().plusSeconds(60));
    }

    private List<Double> embedding() {
        return IntStream.range(0, 768).mapToObj(index -> 0.1D).toList();
    }

    private static class FakeKnowledgeVectorManage extends KnowledgeVectorManage {
        private final List<KnowledgeVectorSearchDTO> rows;
        private int receivedLimit;

        FakeKnowledgeVectorManage(List<KnowledgeVectorSearchDTO> rows) {
            this.rows = rows;
        }

        @Override
        public List<KnowledgeVectorSearchDTO> searchBySemantic(List<String> scenes, String queryEmbedding, int limit) {
            this.receivedLimit = limit;
            return this.rows;
        }
    }

    private static class FakeOcrTaskManage extends OcrTaskManage {
        @Override
        public List<OcrTaskPO> listByTaskNos(Collection<String> taskNos) {
            OcrTaskPO first = new OcrTaskPO();
            first.setTaskNo("ocr-1");
            first.setOriginalFilename("低估值投资方法.pdf");
            OcrTaskPO second = new OcrTaskPO();
            second.setTaskNo("ocr-2");
            second.setOriginalFilename("风险控制.pdf");
            return List.of(first, second);
        }
    }
}
