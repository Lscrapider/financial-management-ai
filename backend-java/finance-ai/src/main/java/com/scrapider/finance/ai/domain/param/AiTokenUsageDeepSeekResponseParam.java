package com.scrapider.finance.ai.domain.param;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class AiTokenUsageDeepSeekResponseParam {

    private String id;
    private String object;
    private String model;
    private Long created;
    private List<JsonNode> choices;
    private JsonNode usage;
    private Map<String, JsonNode> extraFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtraField(String fieldName, JsonNode value) {
        this.extraFields.put(fieldName, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getExtraFields() {
        return this.extraFields;
    }

    public JsonNode toJsonNode(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        if (this.extraFields != null) {
            this.extraFields.forEach(node::set);
        }
        if (this.id != null) {
            node.put("id", this.id);
        }
        if (this.object != null) {
            node.put("object", this.object);
        }
        if (this.model != null) {
            node.put("model", this.model);
        }
        if (this.created != null) {
            node.put("created", this.created);
        }
        if (this.choices != null) {
            node.set("choices", objectMapper.valueToTree(this.choices));
        }
        if (this.usage != null) {
            node.set("usage", this.usage);
        }
        return node;
    }
}
