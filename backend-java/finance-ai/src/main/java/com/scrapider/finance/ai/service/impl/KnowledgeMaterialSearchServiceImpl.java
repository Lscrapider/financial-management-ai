package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeChunkDTO;
import com.scrapider.finance.ai.domain.dto.SceneKnowledgeRetrievalTaskDTO;
import com.scrapider.finance.ai.domain.dto.SceneRetrievalEmbeddingMessageDTO;
import com.scrapider.finance.ai.domain.param.KnowledgeMaterialSearchParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.param.SceneRetrievalEmbeddingParam;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialChunkVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialSubmitVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialTaskVO;
import com.scrapider.finance.ai.publisher.SceneAnalysisMessagePublisher;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.security.SceneAnalysisCallbackTokenStore;
import com.scrapider.finance.ai.service.KnowledgeMaterialQueryRewriteService;
import com.scrapider.finance.ai.service.KnowledgeMaterialSearchService;
import com.scrapider.finance.ai.service.SceneKnowledgeRetrievalService;
import com.scrapider.finance.ai.service.SceneTargetDataProvider;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeMaterialSearchServiceImpl implements KnowledgeMaterialSearchService {

    private static final String SEARCH_MODE_TARGET = "target";
    private static final String SEARCH_MODE_NATURAL_LANGUAGE = "natural_language";
    private static final String GENERIC_SCENE = "knowledge";
    private static final String MATERIAL_TASK_PREFIX = "material-";
    private static final String MATERIAL_CALLBACK_PATH = "/api/ai/knowledge-material/tasks/{taskNo}/callback";
    private static final String MATERIAL_REPORT_TYPE = "knowledge_material";
    private static final String MATERIAL_CONFIG_PROFILE = "knowledge_material";
    private static final String NATURAL_TARGET_TYPE = "KNOWLEDGE_QUERY";
    private static final String NATURAL_TARGET_CODE = "NATURAL_LANGUAGE";
    private static final int SEMANTIC_CANDIDATE_LIMIT = 200;
    private static final int EXPECTED_EMBEDDING_DIMENSION = 768;

    private final ObjectMapper objectMapper;
    private final SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher;
    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneKnowledgeRetrievalService sceneKnowledgeRetrievalService;
    private final SceneAnalysisCallbackTokenStore callbackTokenStore;
    private final List<SceneTargetDataProvider> targetDataProviders;
    private final KnowledgeMaterialQueryRewriteService queryRewriteService;
    private final KnowledgeVectorManage knowledgeVectorManage;
    private final OcrTaskManage ocrTaskManage;

    public KnowledgeMaterialSearchServiceImpl(
            ObjectMapper objectMapper,
            SceneAnalysisMessagePublisher sceneAnalysisMessagePublisher,
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneKnowledgeRetrievalService sceneKnowledgeRetrievalService,
            SceneAnalysisCallbackTokenStore callbackTokenStore,
            List<SceneTargetDataProvider> targetDataProviders,
            KnowledgeMaterialQueryRewriteService queryRewriteService,
            KnowledgeVectorManage knowledgeVectorManage,
            OcrTaskManage ocrTaskManage) {
        this.objectMapper = objectMapper;
        this.sceneAnalysisMessagePublisher = sceneAnalysisMessagePublisher;
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneKnowledgeRetrievalService = sceneKnowledgeRetrievalService;
        this.callbackTokenStore = callbackTokenStore;
        this.targetDataProviders = targetDataProviders;
        this.queryRewriteService = queryRewriteService;
        this.knowledgeVectorManage = knowledgeVectorManage;
        this.ocrTaskManage = ocrTaskManage;
    }

    @Override
    public KnowledgeMaterialSubmitVO submit(KnowledgeMaterialSearchParam param) {
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String searchMode = this.normalizeSearchMode(param.searchMode());
        if (SEARCH_MODE_TARGET.equals(searchMode)) {
            return this.submitTarget(param);
        }
        return this.submitNaturalLanguage(param);
    }

    @Override
    public void callback(String taskNo, String callbackToken, SceneAnalysisCallbackParam param) {
        if (StrUtil.isBlank(taskNo)) {
            throw new IllegalArgumentException("taskNo is required");
        }
        if (param == null) {
            throw new IllegalArgumentException("request body is required");
        }
        try {
            if (param.currentScenesPayload() != null) {
                this.continueWithCurrentScenes(taskNo, callbackToken, param.currentScenesPayload());
                return;
            }
            if (param.retrievalEmbeddings() != null && !param.retrievalEmbeddings().isEmpty()) {
                this.continueWithRetrievalEmbeddings(taskNo, param.retrievalEmbeddings());
                this.callbackTokenStore.revoke(taskNo);
                return;
            }
            throw new IllegalArgumentException("currentScenesPayload or retrievalEmbeddings is required");
        } catch (Exception ex) {
            this.callbackTokenStore.revoke(taskNo);
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public KnowledgeMaterialTaskVO detail(String taskNo) {
        SceneAnalysisTaskPO task = this.requireMaterialTask(taskNo);
        this.checkOwner(task);
        JsonNode reportPayload = task.getReportPayload();
        JsonNode knowledgeContext = this.objectNode(reportPayload == null ? null : reportPayload.path("knowledgeContext"));
        return new KnowledgeMaterialTaskVO(
                task.getTaskNo(),
                this.searchMode(task),
                task.getTargetType(),
                task.getTargetCode(),
                task.getTargetName(),
                this.configText(task, "queryText"),
                this.configText(task, "rewrittenQuery"),
                task.getStatus(),
                task.getErrorMessage(),
                task.getCurrentScenesPayload(),
                knowledgeContext,
                this.flattenChunks(knowledgeContext),
                this.dateText(task.getSubmittedAt()),
                this.dateText(task.getFinishedAt()));
    }

    private KnowledgeMaterialSubmitVO submitTarget(KnowledgeMaterialSearchParam param) {
        String targetType = this.normalizeTargetType(param.targetType());
        String targetCode = StrUtil.trim(param.targetCode());
        this.requireTotalChunks(param.totalChunks());
        if (StrUtil.hasBlank(targetType, targetCode)) {
            throw new IllegalArgumentException("targetType and targetCode are required");
        }
        Long userId = CurrentUserContext.currentUserId();
        String taskNo = this.newTaskNo();
        SceneAnalysisSubmitParam sceneParam = new SceneAnalysisSubmitParam(
                targetType,
                targetCode,
                param.targetName(),
                param.reportType(),
                param.totalChunks(),
                param.dailyKlineLimit(),
                param.weeklyKlineLimit(),
                param.monthlyKlineLimit(),
                param.configProfile(),
                param.userOverrides());
        SceneAnalysisMessageDTO message = this.targetDataProvider(targetType).buildMessage(taskNo, targetCode, sceneParam);
        this.sceneAnalysisTaskManage.saveTask(this.pendingTask(
                userId,
                message,
                SEARCH_MODE_TARGET,
                null,
                null));
        String callbackToken = this.callbackTokenStore.issue(taskNo);
        try {
            this.sceneAnalysisMessagePublisher.publishCurrentSceneAnalysisMessage(
                    message,
                    callbackToken,
                    MATERIAL_CALLBACK_PATH);
            this.sceneAnalysisTaskManage.markProcessing(taskNo);
        } catch (Exception ex) {
            this.callbackTokenStore.revoke(taskNo);
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
        return new KnowledgeMaterialSubmitVO(
                taskNo,
                SEARCH_MODE_TARGET,
                message.target().type(),
                message.target().code(),
                message.target().name(),
                null,
                null,
                SceneAnalysisTaskStatusEnum.PROCESSING_CURRENT_SCENES.getCode());
    }

    private KnowledgeMaterialSubmitVO submitNaturalLanguage(KnowledgeMaterialSearchParam param) {
        String queryText = StrUtil.trim(param.queryText());
        this.requireTotalChunks(param.totalChunks());
        if (StrUtil.isBlank(queryText)) {
            throw new IllegalArgumentException("queryText is required");
        }
        String rewrittenQuery = StrUtil.blankToDefault(this.queryRewriteService.rewrite(queryText), queryText).trim();
        Long userId = CurrentUserContext.currentUserId();
        String taskNo = this.newTaskNo();
        this.sceneAnalysisTaskManage.saveTask(SceneAnalysisTaskPO.createPending(
                taskNo,
                userId,
                NATURAL_TARGET_TYPE,
                NATURAL_TARGET_CODE,
                "知识库材料",
                MATERIAL_REPORT_TYPE,
                MATERIAL_CONFIG_PROFILE,
                this.materialSnapshot(SEARCH_MODE_NATURAL_LANGUAGE, queryText, rewrittenQuery, null)));
        String callbackToken = this.callbackTokenStore.issue(taskNo);
        try {
            this.sceneAnalysisMessagePublisher.publishRetrievalEmbeddingMessage(
                    SceneRetrievalEmbeddingMessageDTO.create(
                            taskNo,
                            List.of(new SceneKnowledgeRetrievalTaskDTO(
                                    GENERIC_SCENE,
                                    param.totalChunks(),
                                    Map.of(),
                                    rewrittenQuery))),
                    callbackToken,
                    MATERIAL_CALLBACK_PATH);
            this.sceneAnalysisTaskManage.markRetrievingKnowledge(taskNo);
        } catch (Exception ex) {
            this.callbackTokenStore.revoke(taskNo);
            this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            throw ex;
        }
        return new KnowledgeMaterialSubmitVO(
                taskNo,
                SEARCH_MODE_NATURAL_LANGUAGE,
                NATURAL_TARGET_TYPE,
                NATURAL_TARGET_CODE,
                "知识库材料",
                queryText,
                rewrittenQuery,
                SceneAnalysisTaskStatusEnum.RETRIEVING_KNOWLEDGE.getCode());
    }

    private void continueWithCurrentScenes(
            String taskNo,
            String callbackToken,
            SceneAnalysisCurrentScenesPayloadParam currentScenesPayload) {
        this.requireMaterialTask(taskNo);
        this.sceneAnalysisTaskManage.markCurrentScenesReady(
                taskNo,
                this.objectMapper.valueToTree(currentScenesPayload));
        List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks = this.sceneKnowledgeRetrievalService.buildRetrievalTasks(
                this.sceneKnowledgeRetrievalService.allocateChunks(currentScenesPayload),
                currentScenesPayload);
        if (retrievalTasks.isEmpty()) {
            ObjectNode payload = this.resultPayload(
                    SEARCH_MODE_TARGET,
                    null,
                    null,
                    List.of(),
                    Map.of());
            this.sceneAnalysisTaskManage.updateReportPayload(taskNo, payload);
            this.sceneAnalysisTaskManage.markReportSucceeded(taskNo);
            this.callbackTokenStore.revoke(taskNo);
            return;
        }
        this.sceneAnalysisTaskManage.markRetrievingKnowledge(taskNo);
        this.sceneAnalysisMessagePublisher.publishRetrievalEmbeddingMessage(
                SceneRetrievalEmbeddingMessageDTO.create(taskNo, retrievalTasks),
                callbackToken,
                MATERIAL_CALLBACK_PATH);
    }

    private void continueWithRetrievalEmbeddings(
            String taskNo,
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        SceneAnalysisTaskPO task = this.requireMaterialTask(taskNo);
        String searchMode = this.searchMode(task);
        Map<String, List<KnowledgeMaterialChunkVO>> knowledgeContext;
        if (SEARCH_MODE_NATURAL_LANGUAGE.equals(searchMode)) {
            knowledgeContext = Map.of(GENERIC_SCENE, this.retrieveNaturalKnowledge(retrievalEmbeddings.get(0)));
        } else {
            knowledgeContext = this.retrieveTargetKnowledge(retrievalEmbeddings);
        }
        ObjectNode payload = this.resultPayload(
                searchMode,
                this.configText(task, "queryText"),
                this.configText(task, "rewrittenQuery"),
                this.sceneKnowledgeRetrievalService.retrievalTasks(retrievalEmbeddings),
                knowledgeContext);
        this.sceneAnalysisTaskManage.updateReportPayload(taskNo, payload);
        this.sceneAnalysisTaskManage.markReportSucceeded(taskNo);
    }

    private Map<String, List<KnowledgeMaterialChunkVO>> retrieveTargetKnowledge(
            List<SceneRetrievalEmbeddingParam> retrievalEmbeddings) {
        Map<String, List<SceneKnowledgeChunkDTO>> rawContext =
                this.sceneKnowledgeRetrievalService.retrieveKnowledge(retrievalEmbeddings);
        Map<String, String> filenames = this.filenameMap(rawContext.values().stream()
                .flatMap(Collection::stream)
                .map(SceneKnowledgeChunkDTO::taskNo)
                .toList());
        Map<String, List<KnowledgeMaterialChunkVO>> result = new LinkedHashMap<>();
        rawContext.forEach((scene, chunks) -> result.put(scene, chunks.stream()
                .map(chunk -> this.materialChunk(chunk, filenames))
                .toList()));
        return result;
    }

    private List<KnowledgeMaterialChunkVO> retrieveNaturalKnowledge(SceneRetrievalEmbeddingParam retrievalEmbedding) {
        String queryEmbedding = this.formatVector(retrievalEmbedding.queryEmbedding());
        int chunkCount = retrievalEmbedding.chunkCount() == null ? 0 : retrievalEmbedding.chunkCount();
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("retrieval chunkCount must be greater than 0");
        }
        List<KnowledgeVectorSearchDTO> rows = this.knowledgeVectorManage.searchBySemantic(
                List.of(),
                queryEmbedding,
                SEMANTIC_CANDIDATE_LIMIT);
        Map<String, String> filenames = this.filenameMap(rows.stream()
                .map(KnowledgeVectorSearchDTO::getTaskNo)
                .toList());
        return rows.stream()
                .sorted(Comparator
                        .comparingDouble((KnowledgeVectorSearchDTO row) ->
                                row.getSemanticScore() == null ? 0 : row.getSemanticScore())
                        .reversed())
                .limit(chunkCount)
                .map(row -> new KnowledgeMaterialChunkVO(
                        row.getId(),
                        row.getTaskNo(),
                        row.getChunkIndex(),
                        GENERIC_SCENE,
                        filenames.getOrDefault(row.getTaskNo(), row.getTaskNo()),
                        row.getText(),
                        List.of(),
                        this.roundScore(row.getSemanticScore() == null ? 0 : row.getSemanticScore()),
                        0.0,
                        0.0,
                        this.roundScore(row.getSemanticScore() == null ? 0 : row.getSemanticScore())))
                .toList();
    }

    private KnowledgeMaterialChunkVO materialChunk(SceneKnowledgeChunkDTO chunk, Map<String, String> filenames) {
        return new KnowledgeMaterialChunkVO(
                chunk.chunkId(),
                chunk.taskNo(),
                chunk.chunkIndex(),
                chunk.scene(),
                filenames.getOrDefault(chunk.taskNo(), chunk.taskNo()),
                chunk.text(),
                chunk.matchedTags(),
                chunk.semanticScore(),
                chunk.tagMatchScore(),
                chunk.crossSceneScore(),
                chunk.finalScore());
    }

    private ObjectNode resultPayload(
            String searchMode,
            String queryText,
            String rewrittenQuery,
            List<SceneKnowledgeRetrievalTaskDTO> retrievalTasks,
            Map<String, List<KnowledgeMaterialChunkVO>> knowledgeContext) {
        ObjectNode root = this.objectMapper.createObjectNode();
        root.put("searchMode", searchMode);
        if (StrUtil.isNotBlank(queryText)) {
            root.put("queryText", queryText);
        }
        if (StrUtil.isNotBlank(rewrittenQuery)) {
            root.put("rewrittenQuery", rewrittenQuery);
        }
        root.set("retrievalTasks", this.objectMapper.valueToTree(retrievalTasks));
        root.set("knowledgeContext", this.objectMapper.valueToTree(knowledgeContext));
        return root;
    }

    private SceneAnalysisTaskPO pendingTask(
            Long userId,
            SceneAnalysisMessageDTO message,
            String searchMode,
            String queryText,
            String rewrittenQuery) {
        return SceneAnalysisTaskPO.createPending(
                message.taskNo(),
                userId,
                message.target().type(),
                message.target().code(),
                message.target().name(),
                message.reportType(),
                message.config().profile(),
                this.materialSnapshot(searchMode, queryText, rewrittenQuery, message));
    }

    private ObjectNode materialSnapshot(
            String searchMode,
            String queryText,
            String rewrittenQuery,
            SceneAnalysisMessageDTO message) {
        ObjectNode snapshot = this.objectMapper.createObjectNode();
        snapshot.put("searchMode", searchMode);
        if (StrUtil.isNotBlank(queryText)) {
            snapshot.put("queryText", queryText);
        }
        if (StrUtil.isNotBlank(rewrittenQuery)) {
            snapshot.put("rewrittenQuery", rewrittenQuery);
        }
        if (message != null && message.config() != null) {
            snapshot.put("configProfile", message.config().profile());
            snapshot.set("parameters", this.objectMapper.valueToTree(message.config().parameters()));
        }
        return snapshot;
    }

    private SceneAnalysisTaskPO requireMaterialTask(String taskNo) {
        if (StrUtil.isBlank(taskNo) || !taskNo.startsWith(MATERIAL_TASK_PREFIX)) {
            throw new IllegalArgumentException("knowledge material task not found: " + taskNo);
        }
        SceneAnalysisTaskPO task = this.sceneAnalysisTaskManage.findByTaskNo(taskNo);
        if (task == null) {
            throw new IllegalArgumentException("knowledge material task not found: " + taskNo);
        }
        return task;
    }

    private void checkOwner(SceneAnalysisTaskPO task) {
        if (CurrentUserContext.isAdmin()) {
            return;
        }
        if (!Objects.equals(task.getUserId(), CurrentUserContext.currentUserId())) {
            throw new IllegalArgumentException("knowledge material task not found: " + task.getTaskNo());
        }
    }

    private SceneTargetDataProvider targetDataProvider(String targetType) {
        return this.targetDataProviders.stream()
                .filter(provider -> provider.supports(targetType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported targetType: " + targetType));
    }

    private String normalizeSearchMode(String searchMode) {
        if (StrUtil.isBlank(searchMode)) {
            throw new IllegalArgumentException("searchMode is required");
        }
        String normalized = searchMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SEARCH_MODE_TARGET -> SEARCH_MODE_TARGET;
            case "query", "natural", "text", SEARCH_MODE_NATURAL_LANGUAGE -> SEARCH_MODE_NATURAL_LANGUAGE;
            default -> throw new IllegalArgumentException("unsupported searchMode: " + searchMode);
        };
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

    private void requireTotalChunks(Integer totalChunks) {
        if (totalChunks == null || totalChunks <= 0) {
            throw new IllegalArgumentException("totalChunks is required and must be greater than 0");
        }
    }

    private String newTaskNo() {
        return MATERIAL_TASK_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String searchMode(SceneAnalysisTaskPO task) {
        return this.configText(task, "searchMode", SEARCH_MODE_TARGET);
    }

    private String configText(SceneAnalysisTaskPO task, String field) {
        return this.configText(task, field, null);
    }

    private String configText(SceneAnalysisTaskPO task, String field, String defaultValue) {
        JsonNode snapshot = task.getConfigSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            return defaultValue;
        }
        String value = snapshot.path(field).asText(null);
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    private Map<String, String> filenameMap(Collection<String> taskNos) {
        Set<String> filtered = taskNos.stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return this.ocrTaskManage.listByTaskNos(filtered).stream()
                .collect(Collectors.toMap(
                        OcrTaskPO::getTaskNo,
                        task -> StrUtil.blankToDefault(task.getOriginalFilename(), task.getTaskNo()),
                        (left, right) -> left));
    }

    private String formatVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("retrieval queryEmbedding is required");
        }
        if (embedding.size() != EXPECTED_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
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

    private JsonNode objectNode(JsonNode node) {
        return node == null || !node.isObject() ? this.objectMapper.createObjectNode() : node;
    }

    private List<KnowledgeMaterialChunkVO> flattenChunks(JsonNode knowledgeContext) {
        if (knowledgeContext == null || !knowledgeContext.isObject()) {
            return List.of();
        }
        List<KnowledgeMaterialChunkVO> chunks = new ArrayList<>();
        knowledgeContext.properties().forEach(entry -> {
            if (!entry.getValue().isArray()) {
                return;
            }
            ArrayNode array = (ArrayNode) entry.getValue();
            array.forEach(node -> chunks.add(this.objectMapper.convertValue(node, KnowledgeMaterialChunkVO.class)));
        });
        return chunks;
    }

    private String dateText(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }

    private double roundScore(double score) {
        return Math.round(score * 1000000.0) / 1000000.0;
    }
}
