package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.api.DeepSeekChatCompletionApi;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import com.scrapider.finance.ai.service.SceneAnalysisReportGenerationService;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.OcrTaskPO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.manage.OcrTaskManage;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import com.scrapider.finance.manage.SceneAnalysisTaskManage;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisReportGenerationServiceImpl implements SceneAnalysisReportGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SceneAnalysisReportGenerationServiceImpl.class);
    private static final String GENERATION_TYPE_INITIAL = "initial";
    private static final String GENERATION_TYPE_REGENERATE = "regenerate";
    private static final String SYSTEM_PROMPT = """
            你是一个面向个人投资研究的理财分析报告生成助手。
            必须基于输入的 marketContext、currentScenes 和 knowledgeContext 生成结构化 JSON 报告。
            输入字段说明：marketContext 是客观市场上下文，包含行情快照、分时线压缩特征、K 线压缩特征等事实和数值。
            currentScenes 是系统计算出的当前场景结果，包含场景分数、方向、内部标签和 evidence，用于辅助理解当前状态。
            knowledgeContext 是知识库召回内容，包含可引用的 chunk，使用其中观点、方法或经验时必须引用 chunkId。
            三者边界：marketContext 负责事实，currentScenes 负责系统解释，knowledgeContext 负责可引用知识依据。
            marketContext.snapshot 中的现价、涨跌幅等是实时行情快照，按原始行情口径理解。
            marketContext.klineTrends 和 currentScenes.trend.periodTrends 是基于日 K、周 K、月 K 的趋势压缩结果，K 线价格采用复权口径。
            快照现价和 K 线最新收盘价可能因价格口径不同而不同，不得直接比较二者并判断“数据不一致”“数据时滞”或“某周期价格错误”。
            需要提到价格差异时，只能说明“快照和 K 线趋势数据属于不同价格口径”，除非输入中明确提供了时滞或异常字段。
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

    public SceneAnalysisReportGenerationServiceImpl(
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
        this.submitGeneration(taskNo, GENERATION_TYPE_REGENERATE);
    }

    @Override
    public SceneAnalysisReportVO getReport(String taskNo) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage.latestByTaskNo(taskNo);
        if (report == null) {
            return new SceneAnalysisReportVO(
                    task.getTaskNo(),
                    null,
                    task.getStatus(),
                    task.getErrorMessage(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        boolean finished = SceneAnalysisTaskStatusEnum.SUCCESS.getCode().equals(report.getStatus());
        return new SceneAnalysisReportVO(
                task.getTaskNo(),
                report.getId(),
                report.getStatus(),
                report.getErrorMessage(),
                report.getGenerationType(),
                report.getVersionNo(),
                finished ? report.getReportContent() : null,
                finished ? report.getReportText() : null,
                finished ? report.getModel() : null,
                finished && report.getGeneratedAt() != null ? report.getGeneratedAt().toString() : null);
    }

    private void submitGeneration(String taskNo, String generationType) {
        SceneAnalysisTaskPO task = this.reportReadyTask(taskNo);
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

    private void generateFromTaskSnapshotSafely(String taskNo, Long reportId, String generationType) {
        try {
            GeneratedReport report = this.generateFromTaskSnapshot(taskNo, generationType);
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

    private GeneratedReport generateFromTaskSnapshot(String taskNo, String generationType) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        JsonNode currentScenesPayload = this.requiredObject(task.getCurrentScenesPayload(), "currentScenesPayload");
        ObjectNode reportPayload = this.mutableReportPayload(task.getReportPayload());
        JsonNode knowledgeContext = this.requiredObject(reportPayload.path("knowledgeContext"), "knowledgeContext");
        JsonNode requestPayload = this.reportRequestPayload(task, currentScenesPayload, reportPayload, knowledgeContext, generationType);
        Set<Long> allowedChunkIds = this.collectKnowledgeChunkIds(requestPayload.path("knowledgeContext"));
        JsonNode deepSeekResponse = this.deepSeekChatCompletionApi.generateJsonReport(
                SYSTEM_PROMPT,
                this.userPrompt(requestPayload, allowedChunkIds, this.outputRequirement()));
        this.recordTokenUsage(deepSeekResponse);
        JsonNode reportContent = this.reportContent(deepSeekResponse);
        this.validateChunkReferences(reportContent, allowedChunkIds);
        return new GeneratedReport(reportContent, this.renderReportText(reportContent, knowledgeContext));
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
        JsonNode requestPayload = this.buildRequestPayload(task, currentScenesPayload, knowledgeContext);
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
        return task;
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

    private JsonNode buildRequestPayload(
            SceneAnalysisTaskPO task,
            JsonNode currentScenesPayload,
            JsonNode knowledgeContext) {
        ObjectNode root = this.objectMapper.createObjectNode();
        root.set("marketContext", this.reportMarketContext(currentScenesPayload.path("marketContext"), task));
        root.set("currentScenes", this.reportCurrentScenes(currentScenesPayload.path("currentScenes")));
        root.set("knowledgeContext", this.reportKnowledgeContext(knowledgeContext));
        return root;
    }

    private JsonNode reportMarketContext(JsonNode marketContext, SceneAnalysisTaskPO task) {
        if (marketContext != null && marketContext.isObject()) {
            return marketContext;
        }
        ObjectNode fallback = this.objectMapper.createObjectNode();
        ObjectNode snapshot = fallback.putObject("snapshot");
        snapshot.put("targetType", task.getTargetType());
        snapshot.put("targetCode", task.getTargetCode());
        snapshot.put("targetName", task.getTargetName());
        return fallback;
    }

    private ObjectNode reportCurrentScenes(JsonNode currentScenes) {
        ObjectNode result = this.objectMapper.createObjectNode();
        if (currentScenes == null || !currentScenes.isObject()) {
            return result;
        }
        currentScenes.properties().forEach(entry -> {
            JsonNode module = entry.getValue();
            if (module == null || !module.isObject()) {
                return;
            }
            ObjectNode sanitized = result.putObject(entry.getKey());
            this.copyIfPresent(module, sanitized, "score");
            this.copyIfPresent(module, sanitized, "level");
            this.copyIfPresent(module, sanitized, "direction");
            this.copyIfPresent(module, sanitized, "tags");
            this.copyIfPresent(module, sanitized, "evidence");
            this.copyIfPresent(module, sanitized, "periodTrends");
        });
        return result;
    }

    private ObjectNode reportKnowledgeContext(JsonNode knowledgeContext) {
        ObjectNode result = this.objectMapper.createObjectNode();
        if (knowledgeContext == null || !knowledgeContext.isObject()) {
            return result;
        }
        knowledgeContext.properties().forEach(entry -> {
            JsonNode chunks = entry.getValue();
            if (!chunks.isArray()) {
                return;
            }
            ArrayNode sanitizedChunks = result.putArray(entry.getKey());
            chunks.forEach(chunk -> {
                if (chunk == null || !chunk.isObject()) {
                    return;
                }
                ObjectNode sanitized = sanitizedChunks.addObject();
                this.copyIfPresent(chunk, sanitized, "chunkId");
                this.copyIfPresent(chunk, sanitized, "scene");
                this.copyIfPresent(chunk, sanitized, "text");
                this.copyIfPresent(chunk, sanitized, "matchedTags");
            });
        });
        return result;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String fieldName) {
        JsonNode value = source.path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(fieldName, value);
        }
    }

    private ObjectNode outputRequirement() {
        ObjectNode requirement = this.objectMapper.createObjectNode();
        requirement.put("language", "zh-CN");
        requirement.put("format", "json_object");
        requirement.put("mustReferenceChunkIds", true);
        requirement.put("allowTradingSuggestion", true);
        requirement.put("tradingSuggestionMustExplainReason", true);
        requirement.put("noFabricatedData", true);
        requirement.put("chunkIdsFieldName", "chunkIds");
        requirement.put("recommendedSchema", """
                {
                  "summary": {"title": "", "conclusion": "", "confidence": "low|medium|high"},
                  "marketFacts": [{"fact": "", "source": "marketContext|currentScenes|knowledgeContext", "chunkIds": []}],
                  "sceneInterpretation": [{"scene": "", "view": "", "basis": [], "chunkIds": []}],
                  "periodTrendAnalysis": [
                    {"period": "daily", "title": "日K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []},
                    {"period": "weekly", "title": "周K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []},
                    {"period": "monthly", "title": "月K趋势", "trend": "", "basis": [], "interpretation": "", "chunkIds": []}
                  ],
                  "knowledgeBasedAnalysis": [{"scene": "", "point": "", "chunkIds": []}],
                  "tradingSuggestions": [{
                    "action": "buy|sell|hold|watch|avoid",
                    "suggestion": "",
                    "reason": "",
                    "conditions": [],
                    "risks": [],
                    "chunkIds": []
                  }],
                  "riskWarnings": [{"risk": "", "reason": "", "chunkIds": []}],
                  "watchPoints": [{"item": "", "reason": "", "chunkIds": []}],
                  "missingData": [],
                  "conclusion": ""
                }
                """);
        return requirement;
    }

    private String userPrompt(JsonNode requestPayload, Set<Long> allowedChunkIds, JsonNode outputRequirement) {
        return """
                请基于以下 JSON 输入生成投资研究报告 JSON。

                硬性要求：
                1. 输出必须是 JSON object。
                2. 引用知识库内容时必须在对应对象里填写 chunkIds。
                3. chunkIds 只能来自 allowedChunkIds。
                4. 没有知识库依据的判断可以使用空数组 chunkIds: []，但不能编造 chunkId。
                5. 可以给出买入、卖出、持有、观望或回避建议，但必须同时说明 reason、conditions 和 risks。
                6. 操作建议只能作为研究判断，不得承诺收益，不得使用“必涨”“必跌”“一定买入”等确定性表述。
                7. 必须输出 periodTrendAnalysis，且包含 daily、weekly、monthly 三项，分别解释日 K、周 K、月 K 的趋势状态、主要依据和对当前判断的影响。
                8. periodTrendAnalysis 必须优先使用 currentScenes.trend.periodTrends 与 marketContext.klineTrends 中对应周期的数据；只能用自然语言表达，不得暴露内部标签名、字段名或 score。
                9. snapshot 是实时行情快照，K 线趋势数据可能是复权口径；不要把 snapshot 现价和 K 线 latestClose 的差异解释成月线价格、数据错误、数据时滞或多周期不一致。
                10. 如果必须同时提到快照现价和 K 线价格，只能说明它们属于不同价格口径，趋势判断以 K 线趋势结构为准，现价用于描述当前行情快照。

                allowedChunkIds:
                %s

                输出格式要求:
                %s

                输入 JSON:
                %s
                """.formatted(allowedChunkIds, outputRequirement, requestPayload);
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

    private String renderReportText(JsonNode reportContent, JsonNode knowledgeContext) {
        Map<Long, String> chunkReferenceLabels = this.chunkReferenceLabels(knowledgeContext);
        StringBuilder builder = new StringBuilder();
        String title = reportContent.path("summary").path("title").asText("标的分析报告");
        builder.append("# ").append(title).append("\n\n");
        this.appendText(builder, "结论", reportContent.path("conclusion").asText(""));
        this.appendArray(builder, "市场事实", reportContent.path("marketFacts"), "fact", chunkReferenceLabels);
        this.appendArray(builder, "场景解读", reportContent.path("sceneInterpretation"), "view", chunkReferenceLabels);
        this.appendPeriodTrendAnalysis(builder, reportContent.path("periodTrendAnalysis"), chunkReferenceLabels);
        this.appendArray(builder, "知识库分析", reportContent.path("knowledgeBasedAnalysis"), "point", chunkReferenceLabels);
        this.appendTradingSuggestions(builder, reportContent.path("tradingSuggestions"), chunkReferenceLabels);
        this.appendArray(builder, "风险提示", reportContent.path("riskWarnings"), "risk", chunkReferenceLabels);
        this.appendArray(builder, "观察点", reportContent.path("watchPoints"), "item", chunkReferenceLabels);
        return builder.toString().trim();
    }

    private void appendText(StringBuilder builder, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        builder.append("## ").append(title).append("\n").append(text).append("\n\n");
    }

    private void appendArray(
            StringBuilder builder,
            String title,
            JsonNode array,
            String fieldName,
            Map<Long, String> chunkReferenceLabels) {
        if (!array.isArray() || array.size() == 0) {
            return;
        }
        builder.append("## ").append(title).append("\n");
        for (JsonNode item : array) {
            String text = item.path(fieldName).asText("");
            if (text.isBlank()) {
                text = item.path("reason").asText("");
            }
            if (!text.isBlank()) {
                builder.append("- ").append(text);
                ArrayNode chunkIds = item.has("chunkIds") && item.get("chunkIds").isArray()
                        ? (ArrayNode) item.get("chunkIds")
                        : null;
                if (chunkIds != null && chunkIds.size() > 0) {
                    builder.append("（引用：").append(this.chunkReferences(chunkIds, chunkReferenceLabels)).append("）");
                }
                builder.append("\n");
            }
        }
        builder.append("\n");
    }

    private void appendPeriodTrendAnalysis(
            StringBuilder builder,
            JsonNode array,
            Map<Long, String> chunkReferenceLabels) {
        if (!array.isArray() || array.size() == 0) {
            return;
        }
        builder.append("## 周期趋势分析\n");
        for (JsonNode item : array) {
            String title = item.path("title").asText("");
            if (title.isBlank()) {
                title = this.periodLabel(item.path("period").asText(""));
            }
            String trend = item.path("trend").asText("");
            String interpretation = item.path("interpretation").asText("");
            if (title.isBlank() && trend.isBlank() && interpretation.isBlank()) {
                continue;
            }
            builder.append("- ");
            if (!title.isBlank()) {
                builder.append("【").append(title).append("】");
            }
            if (!trend.isBlank()) {
                builder.append(trend);
            }
            if (!interpretation.isBlank() && !interpretation.equals(trend)) {
                if (!trend.isBlank()) {
                    builder.append("。");
                }
                builder.append(interpretation);
            }
            String basis = this.stringArrayText(item.path("basis"));
            if (!basis.isBlank()) {
                builder.append("（依据：").append(basis).append("）");
            }
            ArrayNode chunkIds = item.has("chunkIds") && item.get("chunkIds").isArray()
                    ? (ArrayNode) item.get("chunkIds")
                    : null;
            if (chunkIds != null && chunkIds.size() > 0) {
                builder.append("（引用：").append(this.chunkReferences(chunkIds, chunkReferenceLabels)).append("）");
            }
            builder.append("\n");
        }
        builder.append("\n");
    }

    private void appendTradingSuggestions(
            StringBuilder builder,
            JsonNode suggestions,
            Map<Long, String> chunkReferenceLabels) {
        if (!suggestions.isArray() || suggestions.size() == 0) {
            return;
        }
        builder.append("## 操作建议\n");
        for (JsonNode item : suggestions) {
            String suggestion = item.path("suggestion").asText("");
            String action = item.path("action").asText("");
            String reason = item.path("reason").asText("");
            if (suggestion.isBlank() && reason.isBlank()) {
                continue;
            }
            String actionLabel = this.actionLabel(action);
            builder.append("- ");
            if (!actionLabel.isBlank()) {
                builder.append("【").append(actionLabel).append("】");
            }
            builder.append(suggestion.isBlank() ? "建议" : suggestion);
            builder.append("\n");
            if (!reason.isBlank() && !reason.equals(suggestion)) {
                builder.append("  - 理由：").append(reason).append("\n");
            }
            String conditions = this.stringArrayText(item.path("conditions"));
            if (!conditions.isBlank()) {
                builder.append("  - 条件：").append(conditions).append("\n");
            }
            String risks = this.stringArrayText(item.path("risks"));
            if (!risks.isBlank()) {
                builder.append("  - 风险：").append(risks).append("\n");
            }
            ArrayNode chunkIds = item.has("chunkIds") && item.get("chunkIds").isArray()
                    ? (ArrayNode) item.get("chunkIds")
                    : null;
            if (chunkIds != null && chunkIds.size() > 0) {
                builder.append("  - 引用：").append(this.chunkReferences(chunkIds, chunkReferenceLabels)).append("\n");
            }
        }
        builder.append("\n");
    }

    private String stringArrayText(JsonNode values) {
        if (!values.isArray() || values.size() == 0) {
            return "";
        }
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private String actionLabel(String action) {
        return switch (action == null ? "" : action.trim()) {
            case "buy" -> "买入";
            case "sell" -> "卖出";
            case "hold" -> "持有";
            case "watch" -> "观望";
            case "avoid" -> "回避";
            default -> "";
        };
    }

    private String periodLabel(String period) {
        return switch (period == null ? "" : period.trim()) {
            case "daily" -> "日K趋势";
            case "weekly" -> "周K趋势";
            case "monthly" -> "月K趋势";
            default -> "";
        };
    }

    private Map<Long, String> chunkReferenceLabels(JsonNode knowledgeContext) {
        Map<Long, String> labels = new LinkedHashMap<>();
        if (knowledgeContext == null || !knowledgeContext.isObject()) {
            return labels;
        }
        Map<Long, String> chunkTaskNos = this.chunkTaskNos(knowledgeContext);
        Map<String, String> filenameMap = this.filenameMap(chunkTaskNos.values());
        chunkTaskNos.forEach((chunkId, taskNo) -> {
            String sourceName = this.sourceName(filenameMap.get(taskNo));
            if (sourceName.isBlank()) {
                sourceName = "知识库";
            }
            labels.put(chunkId, "%s（chunkId: %d）".formatted(sourceName, chunkId));
        });
        return labels;
    }

    private Map<Long, String> chunkTaskNos(JsonNode knowledgeContext) {
        Map<Long, String> chunkTaskNos = new LinkedHashMap<>();
        knowledgeContext.properties().forEach(entry -> {
            JsonNode chunks = entry.getValue();
            if (!chunks.isArray()) {
                return;
            }
            chunks.forEach(chunk -> {
                JsonNode chunkId = chunk.path("chunkId");
                if (!chunkId.isNumber()) {
                    return;
                }
                String taskNo = chunk.path("taskNo").asText("");
                if (taskNo.isBlank()) {
                    return;
                }
                chunkTaskNos.put(chunkId.asLong(), taskNo);
            });
        });
        return chunkTaskNos;
    }

    private Map<String, String> filenameMap(java.util.Collection<String> taskNos) {
        Set<String> normalizedTaskNos = taskNos.stream()
                .filter(taskNo -> taskNo != null && !taskNo.isBlank())
                .collect(java.util.stream.Collectors.toSet());
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

    private String chunkReferences(ArrayNode chunkIds, Map<Long, String> chunkReferenceLabels) {
        return java.util.stream.StreamSupport.stream(chunkIds.spliterator(), false)
                .filter(JsonNode::isNumber)
                .map(JsonNode::asLong)
                .map(id -> chunkReferenceLabels.getOrDefault(id, "chunkId: " + id))
                .distinct()
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private String sourceName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String name = java.nio.file.Path.of(originalFilename).getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0) {
            return name;
        }
        return name.substring(0, dotIndex);
    }

    private void recordTokenUsage(JsonNode response) {
        try {
            this.aiTokenUsageService.recordDeepSeekResponse(response);
        } catch (RuntimeException ignored) {
            // Token usage 记录失败不应覆盖报告生成主流程错误。
        }
    }

    private record GeneratedReport(JsonNode reportContent, String reportText) {
    }
}
