package com.scrapider.finance.ai.handler;

import com.scrapider.finance.service.AssetDataEnsureResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 数据兜底刷新状态的兼容元数据。
 */
final class AgentDataRefreshMetadata {

    private AgentDataRefreshMetadata() {
    }

    static Map<String, Object> completed(List<String> requestedSections, AssetDataEnsureResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attempted", true);
        metadata.put("status", result.completed() ? "completed" : "partial");
        metadata.put("requestedSections", List.copyOf(requestedSections));
        metadata.put("refreshAttempted", result.refreshAttempted());
        metadata.put("unavailableSections", result.unavailableSections());
        return metadata;
    }

    static Map<String, Object> failed(List<String> requestedSections) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attempted", true);
        metadata.put("status", "refresh_failed");
        metadata.put("requestedSections", List.copyOf(requestedSections));
        metadata.put("failureReason", "refresh_failed");
        return metadata;
    }
}
