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
            必须基于输入的 currentScenes 和 knowledgeContext 生成结构化 JSON 报告。
            可以给出买入、卖出、持有、观望或回避建议，但必须解释依据、适用条件和主要风险。
            不要承诺收益，不要编造缺失数据，不要把建议表述为确定性结论。
            必须区分事实、推断和风险提示。
            使用知识库内容时必须引用 chunkId，chunkId 只能来自输入的 knowledgeContext。
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
            GeneratedReport report = this.generateFromTaskSnapshot(taskNo);
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

    private GeneratedReport generateFromTaskSnapshot(String taskNo) {
        SceneAnalysisTaskPO task = this.loadTask(taskNo);
        JsonNode currentScenesPayload = this.requiredObject(task.getCurrentScenesPayload(), "currentScenesPayload");
        ObjectNode reportPayload = this.mutableReportPayload(task.getReportPayload());
        JsonNode knowledgeContext = this.requiredObject(reportPayload.path("knowledgeContext"), "knowledgeContext");
        Set<Long> allowedChunkIds = this.collectKnowledgeChunkIds(knowledgeContext);
        JsonNode requestPayload = this.buildRequestPayload(task, currentScenesPayload, reportPayload, knowledgeContext);
        JsonNode deepSeekResponse = this.deepSeekChatCompletionApi.generateJsonReport(
                SYSTEM_PROMPT,
                this.userPrompt(requestPayload, allowedChunkIds));
        this.recordTokenUsage(deepSeekResponse);
        JsonNode reportContent = this.reportContent(deepSeekResponse);
        this.validateChunkReferences(reportContent, allowedChunkIds);
        return new GeneratedReport(reportContent, this.renderReportText(reportContent, knowledgeContext));
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
            JsonNode reportPayload,
            JsonNode knowledgeContext) {
        ObjectNode root = this.objectMapper.createObjectNode();
        ObjectNode target = root.putObject("target");
        target.put("type", task.getTargetType());
        target.put("code", task.getTargetCode());
        target.put("name", task.getTargetName());
        root.put("reportType", task.getReportType());
        root.set("currentScenes", this.reportCurrentScenes(currentScenesPayload.path("currentScenes")));
        root.set("chunkAllocation", reportPayload.path("chunkAllocation"));
        root.set("retrievalTasks", reportPayload.path("retrievalTasks"));
        root.set("knowledgeContext", knowledgeContext);
        root.set("outputRequirement", this.outputRequirement());
        return root;
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
            this.copyIfPresent(module, sanitized, "queryText");
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
                  "marketFacts": [{"fact": "", "source": "currentScenes|knowledgeContext", "chunkIds": []}],
                  "sceneInterpretation": [{"scene": "", "view": "", "basis": [], "chunkIds": []}],
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

    private String userPrompt(JsonNode requestPayload, Set<Long> allowedChunkIds) {
        return """
                请基于以下 JSON 输入生成投资研究报告 JSON。

                硬性要求：
                1. 输出必须是 JSON object。
                2. 引用知识库内容时必须在对应对象里填写 chunkIds。
                3. chunkIds 只能来自 allowedChunkIds。
                4. 没有知识库依据的判断可以使用空数组 chunkIds: []，但不能编造 chunkId。
                5. 可以给出买入、卖出、持有、观望或回避建议，但必须同时说明 reason、conditions 和 risks。
                6. 操作建议只能作为研究判断，不得承诺收益，不得使用“必涨”“必跌”“一定买入”等确定性表述。

                allowedChunkIds:
                %s

                输入 JSON:
                %s
                """.formatted(allowedChunkIds, requestPayload);
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
