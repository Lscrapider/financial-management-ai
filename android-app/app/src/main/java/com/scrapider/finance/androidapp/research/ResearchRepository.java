package com.scrapider.finance.androidapp.research;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ResearchRepository {
    public interface Callback {
        void onComplete(ApiResult result, ResearchSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private final ApiClient apiClient;

    public ResearchRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        apiClient.get(ApiConfig.REPORT_TARGETS_PATH, result -> {
            ResearchSummary summary = new ResearchSummary();
            if (result.success) {
                readTargets(result.body, summary);
                summary.updatedAt = LocalDateTime.now().format(TIME_FORMAT);
                callback.onComplete(ApiResult.success(200, "", "报告研究后端数据已同步。"), summary);
            } else {
                callback.onComplete(result, summary);
            }
        });
    }

    private void readTargets(String body, ResearchSummary summary) {
        JSONObject root = object(body);
        summary.total = root.optLong("total", 0);
        JSONArray records = root.optJSONArray("records");
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ResearchReportTarget target = ResearchReportTarget.from(item);
            summary.targets.add(target);
            countType(summary, target.type);
            countStatus(summary, target.status);
        }
    }

    private void countType(ResearchSummary summary, String type) {
        if ("STOCK".equals(type)) {
            summary.stockCount++;
        } else if ("INDEX".equals(type)) {
            summary.indexCount++;
        } else if ("BOND".equals(type)) {
            summary.bondCount++;
        }
    }

    private void countStatus(ResearchSummary summary, String status) {
        if ("success".equals(status)) {
            summary.successCount++;
        } else if ("failed".equals(status)) {
            summary.failedCount++;
        } else if ("retrieving_knowledge".equals(status) || "current_scenes_ready".equals(status)) {
            summary.retrievingCount++;
        } else if ("pending".equals(status)) {
            summary.pendingCount++;
        } else if ("processing_current_scenes".equals(status) || "generating_report".equals(status)) {
            summary.generatingCount++;
        }
    }

    private JSONObject object(String body) {
        try {
            return new JSONObject(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }
}
