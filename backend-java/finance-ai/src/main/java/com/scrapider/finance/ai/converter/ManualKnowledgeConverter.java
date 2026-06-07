package com.scrapider.finance.ai.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;

public final class ManualKnowledgeConverter {

    private ManualKnowledgeConverter() {
    }

    public static JsonNode draftContent(ObjectMapper objectMapper, String taskNo, List<String> chunks) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("taskNo", taskNo);
        root.put("paragraphCount", chunks.size());
        root.put("createdAt", LocalDateTime.now().toString());
        ObjectNode metrics = root.putObject("metrics");
        metrics.put("paragraphCount", chunks.size());
        metrics.put("emptySegmentCount", 0);
        metrics.put("warningCount", 0);
        metrics.put("lowConfidenceParagraphCount", 0);
        metrics.put("avgConfidence", 1);
        ArrayNode paragraphs = root.putArray("paragraphs");
        for (int index = 0; index < chunks.size(); index++) {
            ObjectNode paragraph = paragraphs.addObject();
            paragraph.put("paragraphNo", index + 1);
            paragraph.put("text", chunks.get(index));
            paragraph.putArray("sourcePages");
            paragraph.putArray("sourceSegments");
            paragraph.put("avgConfidence", 1);
            paragraph.putArray("warnings");
        }
        return root;
    }
}
