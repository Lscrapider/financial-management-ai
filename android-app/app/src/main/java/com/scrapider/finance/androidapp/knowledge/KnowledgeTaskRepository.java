package com.scrapider.finance.androidapp.knowledge;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class KnowledgeTaskRepository {
    public interface Callback {
        void onComplete(ApiResult result, KnowledgeTaskSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private final ApiClient apiClient;

    public KnowledgeTaskRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        KnowledgeTaskSummary summary = new KnowledgeTaskSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.postJson(ApiConfig.OCR_TASK_PAGE_PATH, new JSONObject(), result -> {
            if (result.success) {
                readPage(result.body, summary, true);
            } else {
                pending.captureFailure(result, "文字识别任务同步失败。");
            }
            pending.done();
        });
        apiClient.postJson(ApiConfig.MANUAL_KNOWLEDGE_TASK_PAGE_PATH, new JSONObject(), result -> {
            if (result.success) {
                readPage(result.body, summary, false);
            } else {
                pending.captureFailure(result, "手动知识任务同步失败。");
            }
            pending.done();
        });
    }

    private void readPage(String body, KnowledgeTaskSummary summary, boolean ocr) {
        JSONObject root = object(body);
        if (ocr) {
            summary.ocrTotal = root.optLong("total", 0);
        } else {
            summary.manualTotal = root.optLong("total", 0);
        }
        JSONArray records = root.optJSONArray("records");
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) {
                continue;
            }
            countStatus(summary, item.optString("status", ""));
            summary.segmentCount += item.optInt("segmentCount", 0);
            if (summary.latestTaskNo.isEmpty()) {
                summary.latestTaskNo = item.optString("taskNo", "");
                summary.latestFilename = item.optString("originalFilename", "");
                summary.latestStage = item.optString("currentStage", "");
                summary.latestStatus = item.optString("status", "");
                summary.latestProgress = item.optInt("progress", 0);
            }
        }
    }

    private void countStatus(KnowledgeTaskSummary summary, String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("success") || normalized.contains("complete") || normalized.contains("done")) {
            summary.completedCount++;
        } else if (normalized.contains("fail") || normalized.contains("error")) {
            summary.failedCount++;
        } else if (normalized.contains("review")) {
            summary.reviewCount++;
        } else if (!normalized.isEmpty()) {
            summary.processingCount++;
        }
    }

    private JSONObject object(String body) {
        try {
            return new JSONObject(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }

    private static final class Pending {
        private final Callback callback;
        private final KnowledgeTaskSummary summary;
        private int remaining = 2;
        private ApiResult failure;

        private Pending(Callback callback, KnowledgeTaskSummary summary) {
            this.callback = callback;
            this.summary = summary;
        }

        private void captureFailure(ApiResult result, String fallback) {
            if (failure == null) {
                String message = result.message == null || result.message.isEmpty() ? fallback : result.message;
                failure = ApiResult.failure(result.statusCode, result.body, message);
            }
        }

        private void done() {
            remaining--;
            if (remaining > 0) {
                return;
            }
            summary.updatedAt = LocalDateTime.now().format(TIME_FORMAT);
            if (failure != null) {
                callback.onComplete(failure, summary);
            } else {
                callback.onComplete(ApiResult.success(200, "", "知识库任务已同步。"), summary);
            }
        }
    }
}
