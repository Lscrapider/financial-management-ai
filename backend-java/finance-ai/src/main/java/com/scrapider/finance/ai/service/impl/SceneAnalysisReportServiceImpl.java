package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.api.DeepSeekChatCompletionApi;
import com.scrapider.finance.ai.converter.SceneAnalysisReportConverter;
import com.scrapider.finance.ai.converter.SceneAnalysisReportPayloadConverter;
import com.scrapider.finance.ai.converter.SceneAnalysisReportTextConverter;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.service.SceneAnalysisReportService;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import com.scrapider.finance.domain.enums.AiTokenUsagePhaseEnum;
import com.scrapider.finance.domain.enums.AiTokenUsageSourceEnum;
import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SceneAnalysisReportServiceImpl implements SceneAnalysisReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SceneAnalysisReportServiceImpl.class);
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String GENERATION_TYPE_INITIAL = "initial";
    private static final String GENERATION_TYPE_REGENERATE = "regenerate";
    private static final String SYSTEM_PROMPT = """
            你是一个面向个人投资研究的理财分析报告生成助手。
            必须基于输入的 marketContext、currentScenes 和 knowledgeContext 生成结构化 JSON 报告。
            输入字段说明：marketContext 是客观市场上下文，包含行情快照、分时线压缩特征、K 线压缩特征等事实和数值。
            marketContext 中各数据块统一包含 meta 和 data：meta 是中文数据口径、用途和限制说明，data 是可引用的客观事实数值。
            生成报告时必须优先遵守每个 marketContext 数据块 meta.限制，不得跨数据范围滥用字段。
            currentScenes 是系统计算出的当前场景结果，包含场景分数、方向、内部标签和 evidence，用于辅助理解当前状态。
            knowledgeContext 是知识库召回内容，包含可引用的 chunk，使用其中观点、方法或经验时必须引用 chunkId。
            三者边界：marketContext 负责事实，currentScenes 负责系统解释，knowledgeContext 负责可引用知识依据。
            具体字段含义以 marketContext 各数据块 meta 为准，不得把不同数据范围或不同价格口径的数据直接比较为异常。
            可以给出买入、卖出、持有、观望或回避建议，但必须解释依据、适用条件和主要风险。
            不要承诺收益，不要编造缺失数据，不要把建议表述为确定性结论。
            必须区分事实、推断和风险提示。
            使用知识库内容时必须引用 chunkId，chunkId 只能来自输入的 knowledgeContext。
            报告面向普通用户，不得在输出内容中直接暴露内部标签名、字段名、score 或系统术语。
            例如不要写 price_drop、volume_expand、risk_control、currentScenes、knowledgeContext、score、标签触发等表达。
            如果需要表达内部标签含义，必须改写成自然语言，例如把“price_drop 标签触发”改写为“当日价格回落”。
            只输出 JSON object，不要输出 Markdown，不要输出 JSON 之外的解释文本。
            """;

    private final SceneAnalysisTaskManage sceneAnalysisTaskManage;
    private final SceneAnalysisReportManage sceneAnalysisReportManage;
    private final OcrTaskManage ocrTaskManage;
    private final DeepSeekChatCompletionApi deepSeekChatCompletionApi;
    private final AiTokenUsageService aiTokenUsageService;
    private final ObjectMapper objectMapper;
    private final Executor sceneAnalysisReportExecutor;

    public SceneAnalysisReportServiceImpl(
            SceneAnalysisTaskManage sceneAnalysisTaskManage,
            SceneAnalysisReportManage sceneAnalysisReportManage,
            OcrTaskManage ocrTaskManage,
            DeepSeekChatCompletionApi deepSeekChatCompletionApi,
            AiTokenUsageService aiTokenUsageService,
            ObjectMapper objectMapper,
            @Qualifier("sceneAnalysisReportExecutor") Executor sceneAnalysisReportExecutor) {
        this.sceneAnalysisTaskManage = sceneAnalysisTaskManage;
        this.sceneAnalysisReportManage = sceneAnalysisReportManage;
        this.ocrTaskManage = ocrTaskManage;
        this.deepSeekChatCompletionApi = deepSeekChatCompletionApi;
        this.aiTokenUsageService = aiTokenUsageService;
        this.objectMapper = objectMapper;
        this.sceneAnalysisReportExecutor = sceneAnalysisReportExecutor;
    }

    @Override
    public void generateAfterKnowledgeRetrieved(String taskNo) {
        this.submitGeneration(taskNo, GENERATION_TYPE_INITIAL);
    }

    @Override
    public void regenerateFromStoredContext(String taskNo) {
        this.submitGenerationForCurrentUser(taskNo, GENERATION_TYPE_REGENERATE);
    }

    @Override
    public SceneAnalysisReportVO getReport(String taskNo) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        this.requireCurrentUserCanAccess(task);
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage.latestByTaskNo(taskNo);
        if (report == null) {
            return SceneAnalysisReportConverter.notGenerated(task);
        }
        return SceneAnalysisReportConverter.latest(task, report);
    }

    @Override
    public SceneAnalysisReportTargetPageVO pageTargets(
            int pageNum,
            int pageSize,
            String targetName,
            String targetCode,
            String targetType) {
        int pn = Math.max(pageNum, DEFAULT_PAGE_NUM);
        int ps = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedTargetName = this.normalizeText(targetName);
        String normalizedTargetCode = this.normalizeText(targetCode);
        String normalizedTargetType = this.normalizeText(targetType);
        Long ownerUserId = CurrentUserContext.ownerUserIdForQuery();
        Long total = this.sceneAnalysisReportManage
                .countTargets(normalizedTargetName, normalizedTargetCode, normalizedTargetType, ownerUserId);
        List<SceneAnalysisReportTargetVO> records = this.sceneAnalysisReportManage
                .listTargets(
                        normalizedTargetName,
                        normalizedTargetCode,
                        normalizedTargetType,
                        ownerUserId,
                        ps,
                        (long) (pn - 1) * ps)
                .stream()
                .map(SceneAnalysisReportConverter::target)
                .toList();
        return SceneAnalysisReportConverter.targetPage(records, total, pn, ps);
    }

    @Override
    public List<SceneAnalysisReportHistoryVO> listHistory(String targetType, String targetCode) {
        if (StrUtil.isBlank(targetType)) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetCode is required");
        }
        return this.sceneAnalysisReportManage
                .listHistory(targetType, targetCode, CurrentUserContext.ownerUserIdForQuery())
                .stream()
                .map(SceneAnalysisReportConverter::history)
                .toList();
    }

    @Override
    public SceneAnalysisReportDetailVO detail(Long reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId is required");
        }
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage
                .findByIdForOwner(reportId, CurrentUserContext.ownerUserIdForQuery());
        if (report == null) {
            throw new IllegalArgumentException("scene analysis report not found: " + reportId);
        }
        return SceneAnalysisReportConverter.detail(report);
    }

    private void submitGeneration(String taskNo, String generationType) {
        SceneAnalysisTaskPO task = this.reportReadyTask(taskNo);
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage.createGeneratingReport(task, generationType);
        this.sceneAnalysisReportExecutor.execute(
                () -> this.generateFromTaskSnapshotSafely(taskNo, report.getId(), generationType));
    }

    private void submitGenerationForCurrentUser(String taskNo, String generationType) {
        SceneAnalysisTaskPO task = this.reportReadyTask(taskNo);
        this.requireCurrentUserCanAccess(task);
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage.createGeneratingReport(task, generationType);
        this.sceneAnalysisReportExecutor.execute(
                () -> this.generateFromTaskSnapshotSafely(taskNo, report.getId(), generationType));
    }

    private SceneAnalysisTaskPO reportReadyTask(String taskNo) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        this.requiredObject(task.getCurrentScenesPayload(), "currentScenesPayload");
        ObjectNode reportPayload = this.mutableReportPayload(task.getReportPayload());
        this.requiredObject(reportPayload.path("knowledgeContext"), "knowledgeContext");
        return task;
    }

    private void requireCurrentUserCanAccess(SceneAnalysisTaskPO task) {
        if (CurrentUserContext.isAdmin()) {
            return;
        }
        if (!Objects.equals(task.getUserId(), CurrentUserContext.currentUserId())) {
            throw new IllegalArgumentException("scene analysis task not found: " + task.getTaskNo());
        }
    }

    private void generateFromTaskSnapshotSafely(String taskNo, Long reportId, String generationType) {
        try {
            GeneratedReport report = this.generateFromTaskSnapshot(taskNo, reportId, generationType);
            this.sceneAnalysisReportManage.markSuccess(
                    reportId,
                    report.reportContent(),
                    report.reportText(),
                    this.deepSeekChatCompletionApi.model());
            if (GENERATION_TYPE_INITIAL.equals(generationType)) {
                this.sceneAnalysisTaskManage.markReportSucceeded(taskNo);
            }
        } catch (RuntimeException ex) {
            LOGGER.error("scene analysis report generation failed task_no={}", taskNo, ex);
            this.sceneAnalysisReportManage.markFailed(reportId, ex.getMessage());
            if (GENERATION_TYPE_INITIAL.equals(generationType)) {
                this.sceneAnalysisTaskManage.markFailed(taskNo, ex.getMessage());
            }
        }
    }

    private GeneratedReport generateFromTaskSnapshot(String taskNo, Long reportId, String generationType) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        JsonNode currentScenesPayload = this.requiredObject(task.getCurrentScenesPayload(), "currentScenesPayload");
        ObjectNode reportPayload = this.mutableReportPayload(task.getReportPayload());
        JsonNode knowledgeContext = this.requiredObject(reportPayload.path("knowledgeContext"), "knowledgeContext");
        JsonNode requestPayload = this.reportRequestPayload(task, currentScenesPayload, reportPayload, knowledgeContext, generationType);
        Set<Long> allowedChunkIds = this.collectKnowledgeChunkIds(requestPayload.path("knowledgeContext"));
        log.info("调用 deepseek 请求开始 {}", taskNo);
        JsonNode deepSeekResponse = this.deepSeekChatCompletionApi.generateJsonReport(
                SYSTEM_PROMPT,
                SceneAnalysisReportPayloadConverter.userPrompt(
                        requestPayload,
                        allowedChunkIds,
                        SceneAnalysisReportPayloadConverter.outputRequirement(this.objectMapper)));
        log.info("调用 deepseek 请求结束 {}", taskNo);
        this.recordTokenUsage(deepSeekResponse, task, reportId);
        JsonNode reportContent = this.reportContent(deepSeekResponse);
        this.validateChunkReferences(reportContent, allowedChunkIds);
        return new GeneratedReport(
                reportContent,
                SceneAnalysisReportTextConverter.renderReportText(reportContent, knowledgeContext, this::filenameMap));
    }

    private JsonNode reportRequestPayload(
            SceneAnalysisTaskPO task,
            JsonNode currentScenesPayload,
            ObjectNode reportPayload,
            JsonNode knowledgeContext,
            String generationType) {
        JsonNode storedLlmInput = reportPayload.path("llmInput");
        if (GENERATION_TYPE_REGENERATE.equals(generationType) && storedLlmInput.isObject()) {
            return storedLlmInput;
        }
        JsonNode requestPayload = SceneAnalysisReportPayloadConverter.requestPayload(
                this.objectMapper,
                task,
                currentScenesPayload,
                knowledgeContext);
        reportPayload.set("llmInput", requestPayload);
        this.sceneAnalysisTaskManage.updateReportPayload(task.getTaskNo(), reportPayload);
        return requestPayload;
    }

    private SceneAnalysisTaskPO loadTask(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new IllegalArgumentException("taskNo is required");
        }
        SceneAnalysisTaskPO task = this.sceneAnalysisTaskManage.findByTaskNo(taskNo);
        if (task == null) {
            throw new IllegalArgumentException("scene analysis task not found: " + taskNo);
        }
        this.requireReportTask(task);
        return task;
    }

    private void requireReportTask(SceneAnalysisTaskPO task) {
        try {
            SceneAnalysisReportTypeEnum.of(task.getReportType());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scene analysis report task not found: " + task.getTaskNo());
        }
    }

    private JsonNode requiredObject(JsonNode node, String name) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return node;
    }

    private ObjectNode mutableReportPayload(JsonNode reportPayload) {
        if (reportPayload == null || reportPayload.isNull() || reportPayload.isMissingNode()) {
            return this.objectMapper.createObjectNode();
        }
        if (!reportPayload.isObject()) {
            throw new IllegalArgumentException("reportPayload must be object");
        }
        return reportPayload.deepCopy();
    }

    private JsonNode reportContent(JsonNode deepSeekResponse) {
        String content = deepSeekResponse
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
        if (content.isBlank()) {
            throw new IllegalArgumentException("DeepSeek response content is empty");
        }
        try {
            JsonNode node = this.objectMapper.readTree(content);
            if (!node.isObject()) {
                throw new IllegalArgumentException("DeepSeek report content must be JSON object");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("DeepSeek report content is not valid JSON", ex);
        }
    }

    private Set<Long> collectKnowledgeChunkIds(JsonNode knowledgeContext) {
        Set<Long> ids = new LinkedHashSet<>();
        knowledgeContext.properties().forEach(entry -> {
            JsonNode chunks = entry.getValue();
            if (chunks.isArray()) {
                chunks.forEach(chunk -> {
                    JsonNode chunkId = chunk.path("chunkId");
                    if (chunkId.isNumber()) {
                        ids.add(chunkId.asLong());
                    }
                });
            }
        });
        return ids;
    }

    private void validateChunkReferences(JsonNode reportContent, Set<Long> allowedChunkIds) {
        Set<Long> referenced = new LinkedHashSet<>();
        this.collectReferencedChunkIds(reportContent, referenced);
        Set<Long> invalid = referenced.stream()
                .filter(id -> !allowedChunkIds.contains(id))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("reportContent contains invalid chunkIds: " + invalid);
        }
    }

    private void collectReferencedChunkIds(JsonNode node, Set<Long> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode chunkIds = node.path("chunkIds");
            if (chunkIds.isArray()) {
                chunkIds.forEach(item -> {
                    if (item.isNumber()) {
                        result.add(item.asLong());
                    }
                });
            }
            node.properties().forEach(entry -> this.collectReferencedChunkIds(entry.getValue(), result));
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> this.collectReferencedChunkIds(item, result));
        }
    }

    private Map<String, String> filenameMap(java.util.Collection<String> taskNos) {
        Set<String> normalizedTaskNos = SceneAnalysisReportTextConverter.normalizedTaskNos(taskNos);
        if (normalizedTaskNos.isEmpty()) {
            return Map.of();
        }
        return this.ocrTaskManage.listByTaskNos(normalizedTaskNos).stream()
                .collect(java.util.stream.Collectors.toMap(
                        OcrTaskPO::getTaskNo,
                        OcrTaskPO::getOriginalFilename,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String normalizeText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        return text.trim();
    }

    private void recordTokenUsage(JsonNode response, SceneAnalysisTaskPO task, Long reportId) {
        try {
            this.aiTokenUsageService.recordDeepSeekResponse(
                    response,
                    task.getUserId(),
                    AiTokenUsageSourceEnum.REPORT,
                    String.valueOf(reportId),
                    AiTokenUsagePhaseEnum.REPORT_GENERATE);
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to record scene analysis token usage.", ex);
        }
    }

    private record GeneratedReport(JsonNode reportContent, String reportText) {
    }
}
