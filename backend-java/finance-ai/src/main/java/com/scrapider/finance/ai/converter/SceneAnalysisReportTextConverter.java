package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class SceneAnalysisReportTextConverter {

    private SceneAnalysisReportTextConverter() {
    }

    public static String renderReportText(
            JsonNode reportContent,
            JsonNode knowledgeContext,
            Function<Collection<String>, Map<String, String>> filenameResolver) {
        Map<Long, String> chunkReferenceLabels = chunkReferenceLabels(knowledgeContext, filenameResolver);
        StringBuilder builder = new StringBuilder();
        String title = reportContent.path("summary").path("title").asText("标的分析报告");
        builder.append("# ").append(title).append("\n\n");
        appendText(builder, "结论", reportContent.path("conclusion").asText(""));
        appendArray(builder, "市场事实", reportContent.path("marketFacts"), "fact", chunkReferenceLabels);
        appendArray(builder, "场景解读", reportContent.path("sceneInterpretation"), "view", chunkReferenceLabels);
        appendPeriodTrendAnalysis(builder, reportContent.path("periodTrendAnalysis"), chunkReferenceLabels);
        appendArray(builder, "知识库分析", reportContent.path("knowledgeBasedAnalysis"), "point", chunkReferenceLabels);
        appendTradingSuggestions(builder, reportContent.path("tradingSuggestions"), chunkReferenceLabels);
        appendArray(builder, "风险提示", reportContent.path("riskWarnings"), "risk", chunkReferenceLabels);
        appendArray(builder, "观察点", reportContent.path("watchPoints"), "item", chunkReferenceLabels);
        return builder.toString().trim();
    }

    public static Map<Long, String> chunkTaskNos(JsonNode knowledgeContext) {
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

    private static Map<Long, String> chunkReferenceLabels(
            JsonNode knowledgeContext,
            Function<Collection<String>, Map<String, String>> filenameResolver) {
        Map<Long, String> labels = new LinkedHashMap<>();
        if (knowledgeContext == null || !knowledgeContext.isObject()) {
            return labels;
        }
        Map<Long, String> chunkTaskNos = chunkTaskNos(knowledgeContext);
        Map<String, String> filenameMap = filenameResolver.apply(chunkTaskNos.values());
        chunkTaskNos.forEach((chunkId, taskNo) -> {
            String sourceName = sourceName(filenameMap.get(taskNo));
            if (sourceName.isBlank()) {
                sourceName = "知识库";
            }
            labels.put(chunkId, "%s（chunkId: %d）".formatted(sourceName, chunkId));
        });
        return labels;
    }

    private static void appendText(StringBuilder builder, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        builder.append("## ").append(title).append("\n").append(text).append("\n\n");
    }

    private static void appendArray(
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
                    builder.append("（引用：").append(chunkReferences(chunkIds, chunkReferenceLabels)).append("）");
                }
                builder.append("\n");
            }
        }
        builder.append("\n");
    }

    private static void appendPeriodTrendAnalysis(
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
                title = periodLabel(item.path("period").asText(""));
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
                if (!trend.isBlank() && !endsWithSentencePunctuation(trend)) {
                    builder.append("。");
                }
                builder.append(interpretation);
            }
            String basis = stringArrayText(item.path("basis"));
            if (!basis.isBlank()) {
                builder.append("（依据：").append(basis).append("）");
            }
            ArrayNode chunkIds = item.has("chunkIds") && item.get("chunkIds").isArray()
                    ? (ArrayNode) item.get("chunkIds")
                    : null;
            if (chunkIds != null && chunkIds.size() > 0) {
                builder.append("（引用：").append(chunkReferences(chunkIds, chunkReferenceLabels)).append("）");
            }
            builder.append("\n");
        }
        builder.append("\n");
    }

    private static void appendTradingSuggestions(
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
            String actionLabel = actionLabel(action);
            builder.append("- ");
            if (!actionLabel.isBlank()) {
                builder.append("【").append(actionLabel).append("】");
            }
            builder.append(suggestion.isBlank() ? "建议" : suggestion);
            builder.append("\n");
            if (!reason.isBlank() && !reason.equals(suggestion)) {
                builder.append("  - 理由：").append(reason).append("\n");
            }
            String conditions = stringArrayText(item.path("conditions"));
            if (!conditions.isBlank()) {
                builder.append("  - 条件：").append(conditions).append("\n");
            }
            String risks = stringArrayText(item.path("risks"));
            if (!risks.isBlank()) {
                builder.append("  - 风险：").append(risks).append("\n");
            }
            ArrayNode chunkIds = item.has("chunkIds") && item.get("chunkIds").isArray()
                    ? (ArrayNode) item.get("chunkIds")
                    : null;
            if (chunkIds != null && chunkIds.size() > 0) {
                builder.append("  - 引用：").append(chunkReferences(chunkIds, chunkReferenceLabels)).append("\n");
            }
        }
        builder.append("\n");
    }

    private static boolean endsWithSentencePunctuation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmedText = text.stripTrailing();
        char lastChar = trimmedText.charAt(trimmedText.length() - 1);
        return lastChar == '。'
                || lastChar == '！'
                || lastChar == '？'
                || lastChar == '.'
                || lastChar == '!'
                || lastChar == '?'
                || lastChar == '；'
                || lastChar == ';';
    }

    private static String stringArrayText(JsonNode values) {
        if (!values.isArray() || values.size() == 0) {
            return "";
        }
        return StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private static String actionLabel(String action) {
        return switch (action == null ? "" : action.trim()) {
            case "buy" -> "买入";
            case "sell" -> "卖出";
            case "hold" -> "持有";
            case "watch" -> "观望";
            case "avoid" -> "回避";
            default -> "";
        };
    }

    private static String periodLabel(String period) {
        return switch (period == null ? "" : period.trim()) {
            case "daily" -> "日K趋势";
            case "weekly" -> "周K趋势";
            case "monthly" -> "月K趋势";
            default -> "";
        };
    }

    private static String chunkReferences(ArrayNode chunkIds, Map<Long, String> chunkReferenceLabels) {
        return StreamSupport.stream(chunkIds.spliterator(), false)
                .filter(JsonNode::isNumber)
                .map(JsonNode::asLong)
                .map(id -> chunkReferenceLabels.getOrDefault(id, "chunkId: " + id))
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private static String sourceName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0) {
            return name;
        }
        return name.substring(0, dotIndex);
    }

    public static Set<String> normalizedTaskNos(Collection<String> taskNos) {
        return taskNos.stream()
                .filter(taskNo -> taskNo != null && !taskNo.isBlank())
                .collect(Collectors.toSet());
    }
}
