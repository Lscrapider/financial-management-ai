package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import java.util.Set;

public final class SceneAnalysisReportPayloadConverter {

    private SceneAnalysisReportPayloadConverter() {
    }

    public static JsonNode requestPayload(
            ObjectMapper objectMapper,
            SceneAnalysisTaskPO task,
            JsonNode currentScenesPayload,
            JsonNode knowledgeContext) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("marketContext", marketContext(objectMapper, currentScenesPayload.path("marketContext"), task));
        root.set("currentScenes", currentScenes(objectMapper, currentScenesPayload.path("currentScenes")));
        root.set("knowledgeContext", knowledgeContext(objectMapper, knowledgeContext));
        return root;
    }

    public static ObjectNode outputRequirement(ObjectMapper objectMapper) {
        ObjectNode requirement = objectMapper.createObjectNode();
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

    public static String userPrompt(JsonNode requestPayload, Set<Long> allowedChunkIds, JsonNode outputRequirement) {
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
                8. periodTrendAnalysis 必须优先使用 currentScenes.trend.periodTrends 与 marketContext.klineTrends.<period>.data 中对应周期的数据；只能用自然语言表达，不得暴露内部标签名、字段名或 score。
                9. 必须遵守 marketContext 各数据块 meta 中的“数据范围”“价格口径”“用途”“限制”；不得跨数据范围滥用字段，不得把不同价格口径的数据差异解释成数据错误、数据时滞或多周期不一致。
                10. 不得输出输入中没有数据支持的内容，例如资金出逃、主力撤退、筹码松动；不得把短周期信号升级成长周期结论。

                allowedChunkIds:
                %s

                输出格式要求:
                %s

                输入 JSON:
                %s
                """.formatted(allowedChunkIds, outputRequirement, requestPayload);
    }

    private static JsonNode marketContext(
            ObjectMapper objectMapper,
            JsonNode marketContext,
            SceneAnalysisTaskPO task) {
        if (marketContext != null && marketContext.isObject()) {
            return marketContext;
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        ObjectNode snapshot = fallback.putObject("snapshot");
        ObjectNode meta = snapshot.putObject("meta");
        meta.put("数据范围", "任务标的基础信息");
        meta.put("用途", "仅用于识别报告标的");
        meta.putArray("限制").add("缺少实时行情快照，不能据此判断价格、趋势或估值");
        ObjectNode data = snapshot.putObject("data");
        data.put("targetType", task.getTargetType());
        data.put("targetCode", task.getTargetCode());
        data.put("targetName", task.getTargetName());
        return fallback;
    }

    private static ObjectNode currentScenes(ObjectMapper objectMapper, JsonNode currentScenes) {
        ObjectNode result = objectMapper.createObjectNode();
        if (currentScenes == null || !currentScenes.isObject()) {
            return result;
        }
        currentScenes.properties().forEach(entry -> {
            JsonNode module = entry.getValue();
            if (module == null || !module.isObject()) {
                return;
            }
            ObjectNode sanitized = result.putObject(entry.getKey());
            copyIfPresent(module, sanitized, "score");
            copyIfPresent(module, sanitized, "level");
            copyIfPresent(module, sanitized, "direction");
            copyIfPresent(module, sanitized, "tags");
            copyIfPresent(module, sanitized, "evidence");
            copyIfPresent(module, sanitized, "periodTrends");
        });
        return result;
    }

    private static ObjectNode knowledgeContext(ObjectMapper objectMapper, JsonNode knowledgeContext) {
        ObjectNode result = objectMapper.createObjectNode();
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
                copyIfPresent(chunk, sanitized, "chunkId");
                copyIfPresent(chunk, sanitized, "scene");
                copyIfPresent(chunk, sanitized, "text");
                copyIfPresent(chunk, sanitized, "matchedTags");
            });
        });
        return result;
    }

    private static void copyIfPresent(JsonNode source, ObjectNode target, String fieldName) {
        JsonNode value = source.path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(fieldName, value);
        }
    }
}
