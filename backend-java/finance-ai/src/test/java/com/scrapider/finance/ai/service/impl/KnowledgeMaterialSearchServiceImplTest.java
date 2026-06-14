package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisConfigDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisTargetDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import com.scrapider.finance.ai.domain.param.KnowledgeMaterialSearchParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesTargetParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSceneModuleParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialSubmitVO;
import com.scrapider.finance.ai.publisher.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.security.SceneAnalysisCallbackTokenStore;
import com.scrapider.finance.ai.service.KnowledgeMaterialQueryRewriteService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class KnowledgeMaterialSearchServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitTargetSearchPersistsMaterialTaskAndPublishesCurrentSceneWithMaterialCallbackPath() {
        this.setCurrentUser(7L);
        FakeTaskManage taskManage = new FakeTaskManage();
        FakePublisher publisher = new FakePublisher();
        FakeTargetDataProvider targetDataProvider = new FakeTargetDataProvider();
        KnowledgeMaterialSearchServiceImpl service = this.service(
                taskManage,
                publisher,
                new FakeKnowledgeVectorManage(List.of()),
                new FakeOcrTaskManage(),
                targetDataProvider);
        KnowledgeMaterialSearchParam param = new KnowledgeMaterialSearchParam(
                "target",
                "STOCK",
                "600000",
                "浦发银行",
                null,
                "risk_check",
                6,
                120,
                52,
                60,
                "custom_profile",
                new SceneAnalysisUserConfigParam(
                        "stock",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        KnowledgeMaterialSubmitVO result = service.submit(param);

        assertThat(result.taskNo()).startsWith("material-");
        assertThat(result.status()).isEqualTo(SceneAnalysisTaskStatusEnum.PROCESSING_CURRENT_SCENES.getCode());
        assertThat(taskManage.savedTask.getTaskNo()).isEqualTo(result.taskNo());
        assertThat(taskManage.savedTask.getUserId()).isEqualTo(7L);
        assertThat(taskManage.savedTask.getTargetType()).isEqualTo("STOCK");
        assertThat(taskManage.processingTaskNo).isEqualTo(result.taskNo());
        assertThat(publisher.currentMessage.taskNo()).isEqualTo(result.taskNo());
        assertThat(publisher.currentCallbackToken).isEqualTo("callback-token");
        assertThat(publisher.currentCallbackPath)
                .isEqualTo("/api/ai/knowledge-material/tasks/{taskNo}/callback");
        assertThat(targetDataProvider.receivedParam.configProfile()).isEqualTo("custom_profile");
        assertThat(targetDataProvider.receivedParam.reportType()).isEqualTo("risk_check");
        assertThat(targetDataProvider.receivedParam.dailyKlineLimit()).isEqualTo(120);
        assertThat(targetDataProvider.receivedParam.userOverrides().assetType()).isEqualTo("stock");
    }

    @Test
    void currentScenesCallbackBuildsRetrievalTasksWithMaterialCallbackPath() {
        FakeTaskManage taskManage = new FakeTaskManage();
        taskManage.existingTask = this.materialTask("material-target", "target");
        FakePublisher publisher = new FakePublisher();
        KnowledgeMaterialSearchServiceImpl service = this.service(
                taskManage,
                publisher,
                new FakeKnowledgeVectorManage(List.of()),
                new FakeOcrTaskManage(),
                new FakeTargetDataProvider());

        service.callback(
                "material-target",
                "callback-token",
                new SceneAnalysisCallbackParam(this.currentScenesPayload(), null));

        assertThat(taskManage.currentScenesReadyTaskNo).isEqualTo("material-target");
        assertThat(taskManage.retrievingTaskNo).isEqualTo("material-target");
        assertThat(publisher.retrievalMessage.taskNo()).isEqualTo("material-target");
        assertThat(publisher.retrievalMessage.retrievalTasks()).hasSize(1);
        assertThat(publisher.retrievalCallbackPath)
                .isEqualTo("/api/ai/knowledge-material/tasks/{taskNo}/callback");
        assertThat(taskManage.succeededTaskNo).isNull();
    }

    @Test
    void naturalLanguageEmbeddingCallbackRetrievesAllKnowledgeAndMarksSuccess() {
        FakeTaskManage taskManage = new FakeTaskManage();
        taskManage.existingTask = this.materialTask("material-query", "natural_language");
        FakeKnowledgeVectorManage knowledgeVectorManage = new FakeKnowledgeVectorManage(this.knowledgeRows());
        FakeOcrTaskManage ocrTaskManage = new FakeOcrTaskManage();
        KnowledgeMaterialSearchServiceImpl service = this.service(
                taskManage,
                new FakePublisher(),
                knowledgeVectorManage,
                ocrTaskManage,
                new FakeTargetDataProvider());

        service.callback(
                "material-query",
                "callback-token",
                new SceneAnalysisCallbackParam(null, List.of(new SceneRetrievalEmbeddingParam(
                        "knowledge",
                        2,
                        Map.of(),
                        "低估值银行股的风险控制",
                        this.embedding()))));

        assertThat(knowledgeVectorManage.receivedScenes).isEmpty();
        assertThat(knowledgeVectorManage.receivedLimit).isEqualTo(200);
        assertThat(taskManage.updatedPayload.path("knowledgeContext").path("knowledge")).hasSize(2);
        assertThat(taskManage.updatedPayload.path("knowledgeContext").path("knowledge").get(0).path("filename").asText())
                .isEqualTo("低估值投资方法.pdf");
        assertThat(taskManage.succeededTaskNo).isEqualTo("material-query");
    }

    private KnowledgeMaterialSearchServiceImpl service(
            FakeTaskManage taskManage,
            FakePublisher publisher,
            FakeKnowledgeVectorManage knowledgeVectorManage,
            FakeOcrTaskManage ocrTaskManage,
            FakeTargetDataProvider targetDataProvider) {
        return new KnowledgeMaterialSearchServiceImpl(
                this.objectMapper,
                publisher,
                taskManage,
                new SceneKnowledgeRetrievalServiceImpl(knowledgeVectorManage),
                new FakeCallbackTokenStore(),
                List.of(targetDataProvider),
                new FakeRewriteService(),
                knowledgeVectorManage,
                ocrTaskManage);
    }

    private SceneAnalysisCurrentScenesPayloadParam currentScenesPayload() {
        SceneAnalysisSceneModuleParam valuation = new SceneAnalysisSceneModuleParam(
                0.8,
                "high",
                "neutral",
                Map.of("low_pb", 0.9),
                List.of(),
                "低估值银行股的风险控制",
                Map.of());
        return new SceneAnalysisCurrentScenesPayloadParam(
                new SceneAnalysisCurrentScenesTargetParam("STOCK", "600000", "浦发银行"),
                "quick_analysis",
                4,
                this.objectMapper.createObjectNode(),
                new SceneAnalysisCurrentScenesParam(null, null, null, null, valuation, null, null));
    }

    private SceneAnalysisTaskPO materialTask(String taskNo, String searchMode) {
        SceneAnalysisTaskPO task = SceneAnalysisTaskPO.createPending(
                taskNo,
                7L,
                "KNOWLEDGE_QUERY",
                "NATURAL_LANGUAGE",
                "知识库材料",
                "knowledge_material",
                "knowledge_material",
                this.objectMapper.valueToTree(Map.of(
                        "searchMode", searchMode,
                        "queryText", "低估值银行股怎么控制风险",
                        "rewrittenQuery", "低估值银行股的风险控制")));
        task.setStatus(SceneAnalysisTaskStatusEnum.RETRIEVING_KNOWLEDGE.getCode());
        return task;
    }

    private List<KnowledgeVectorSearchDTO> knowledgeRows() {
        KnowledgeVectorSearchDTO first = new KnowledgeVectorSearchDTO();
        first.setId(11L);
        first.setTaskNo("ocr-1");
        first.setChunkIndex(1);
        first.setText("低估值不等于低风险，需要配合仓位控制。");
        first.setSemanticScore(0.91);
        first.setMetadata(this.objectMapper.valueToTree(Map.of(
                "scenes", Map.of("valuation", List.of("low_pb")),
                "chunkId", "ocr-1:chunk:0001")));

        KnowledgeVectorSearchDTO second = new KnowledgeVectorSearchDTO();
        second.setId(12L);
        second.setTaskNo("ocr-2");
        second.setChunkIndex(2);
        second.setText("银行股高股息策略要关注资产质量。");
        second.setSemanticScore(0.83);
        second.setMetadata(this.objectMapper.valueToTree(Map.of(
                "scenes", Map.of("risk_strategy", List.of("position_control")),
                "chunkId", "ocr-2:chunk:0002")));
        return List.of(first, second);
    }

    private List<Double> embedding() {
        return IntStream.range(0, 768).mapToObj(index -> 0.1D).toList();
    }

    private void setCurrentUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new Principal(new User(userId)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_admin"))));
    }

    public record Principal(User user) {

        public User getUser() {
            return this.user;
        }
    }

    public record User(Long id) {

        public Long getId() {
            return this.id;
        }
    }

    private static class FakeTaskManage extends SceneAnalysisTaskManage {
        private SceneAnalysisTaskPO savedTask;
        private SceneAnalysisTaskPO existingTask;
        private String processingTaskNo;
        private String currentScenesReadyTaskNo;
        private String retrievingTaskNo;
        private String succeededTaskNo;
        private JsonNode updatedPayload;

        FakeTaskManage() {
            super(new ObjectMapper());
        }

        @Override
        public SceneAnalysisTaskPO saveTask(SceneAnalysisTaskPO task) {
            this.savedTask = task;
            this.existingTask = task;
            return task;
        }

        @Override
        public void markProcessing(String taskNo) {
            this.processingTaskNo = taskNo;
        }

        @Override
        public void markCurrentScenesReady(String taskNo, JsonNode currentScenesPayload) {
            this.currentScenesReadyTaskNo = taskNo;
            this.existingTask.setCurrentScenesPayload(currentScenesPayload);
        }

        @Override
        public void markRetrievingKnowledge(String taskNo) {
            this.retrievingTaskNo = taskNo;
        }

        @Override
        public SceneAnalysisTaskPO findByTaskNo(String taskNo) {
            return this.existingTask;
        }

        @Override
        public void updateReportPayload(String taskNo, JsonNode reportPayload) {
            this.updatedPayload = reportPayload;
            this.existingTask.setReportPayload(reportPayload);
        }

        @Override
        public void markReportSucceeded(String taskNo) {
            this.succeededTaskNo = taskNo;
        }
    }

    private static class FakePublisher extends SceneAnalysisMessagePublisher {
        private SceneAnalysisMessageDTO currentMessage;
        private String currentCallbackToken;
        private String currentCallbackPath;
        private SceneRetrievalEmbeddingMessageDTO retrievalMessage;
        private String retrievalCallbackPath;

        FakePublisher() {
            super(null, new ObjectMapper(), "exchange", "current", "retrieval");
        }

        @Override
        public void publishCurrentSceneAnalysisMessage(
                SceneAnalysisMessageDTO message,
                String callbackToken,
                String callbackPath) {
            this.currentMessage = message;
            this.currentCallbackToken = callbackToken;
            this.currentCallbackPath = callbackPath;
        }

        @Override
        public void publishRetrievalEmbeddingMessage(
                SceneRetrievalEmbeddingMessageDTO message,
                String callbackToken,
                String callbackPath) {
            this.retrievalMessage = message;
            this.retrievalCallbackPath = callbackPath;
        }
    }

    private static class FakeCallbackTokenStore extends SceneAnalysisCallbackTokenStore {

        FakeCallbackTokenStore() {
            super(60);
        }

        @Override
        public String issue(String taskNo) {
            return "callback-token";
        }
    }

    private static class FakeTargetDataProvider implements SceneTargetDataProvider {
        private SceneAnalysisSubmitParam receivedParam;

        @Override
        public boolean supports(String targetType) {
            return "STOCK".equals(targetType);
        }

        @Override
        public SceneAnalysisMessageDTO buildMessage(String taskNo, String targetCode, SceneAnalysisSubmitParam param) {
            this.receivedParam = param;
            return new SceneAnalysisMessageDTO(
                    taskNo,
                    LocalDateTime.now(),
                    param.reportType(),
                    param.totalChunks(),
                    new SceneAnalysisTargetDTO("STOCK", targetCode, param.targetName(), null, null, null),
                    new SceneAnalysisConfigDTO("system_recommended", null),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of());
        }
    }

    private static class FakeRewriteService implements KnowledgeMaterialQueryRewriteService {

        @Override
        public String rewrite(String queryText) {
            return "改写后的 " + queryText;
        }
    }

    private static class FakeKnowledgeVectorManage extends KnowledgeVectorManage {
        private final List<KnowledgeVectorSearchDTO> rows;
        private List<String> receivedScenes;
        private int receivedLimit;

        FakeKnowledgeVectorManage(List<KnowledgeVectorSearchDTO> rows) {
            this.rows = rows;
        }

        @Override
        public List<KnowledgeVectorSearchDTO> searchBySemantic(List<String> scenes, String queryEmbedding, int limit) {
            this.receivedScenes = scenes;
            this.receivedLimit = limit;
            return this.rows;
        }

        @Override
        public List<KnowledgeVectorSearchDTO> searchBySemantic(String scene, String queryEmbedding, int limit) {
            this.receivedScenes = List.of(scene);
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
