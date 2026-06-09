package com.scrapider.finance.api;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TushareApi {

    private static final String DEFAULT_URL = "https://api.tushare.pro";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public TushareApi(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${tushare.base-url:" + DEFAULT_URL + "}") String baseUrl,
            @Value("${tushare.token:${TUSHARE_TOKEN:}}") String token) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public ArrayNode queryRows(String apiName, Map<String, Object> params, String fields) {
        JsonNode response = this.query(apiName, params, fields);
        JsonNode data = response.path("data");
        ArrayNode names = (ArrayNode) data.path("fields");
        ArrayNode items = (ArrayNode) data.path("items");
        ArrayNode rows = this.objectMapper.createArrayNode();
        for (JsonNode item : items) {
            ObjectNode row = this.objectMapper.createObjectNode();
            for (int index = 0; index < names.size(); index++) {
                row.set(names.path(index).asText(), item.path(index));
            }
            rows.add(row);
        }
        return rows;
    }

    private JsonNode query(String apiName, Map<String, Object> params, String fields) {
        if (StrUtil.isBlank(this.token)) {
            throw new IllegalStateException("tushare token is blank, configure tushare.token or TUSHARE_TOKEN");
        }
        ObjectNode request = this.objectMapper.createObjectNode();
        request.put("api_name", apiName);
        request.put("token", this.token);
        request.set("params", this.params(params));
        request.put("fields", fields);

        String body = this.restTemplate.postForObject(this.baseUrl, request, String.class);
        JsonNode response = this.jsonNode(body);
        int code = response.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalArgumentException("tushare api failed: " + response.path("msg").asText());
        }
        return response;
    }

    private ObjectNode params(Map<String, Object> params) {
        ObjectNode node = this.objectMapper.createObjectNode();
        if (params == null || params.isEmpty()) {
            return node;
        }
        Iterator<Map.Entry<String, Object>> iterator = params.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            node.putPOJO(entry.getKey(), entry.getValue());
        }
        return node;
    }

    private JsonNode jsonNode(String body) {
        try {
            return this.objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid tushare api json response", ex);
        }
    }
}
