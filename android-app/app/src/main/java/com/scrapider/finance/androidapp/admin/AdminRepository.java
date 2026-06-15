package com.scrapider.finance.androidapp.admin;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AdminRepository {
    public interface Callback {
        void onComplete(ApiResult result, AdminSummary summary);
    }

    private final ApiClient apiClient;

    public AdminRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        AdminSummary summary = new AdminSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.get(ApiConfig.AI_CONSOLE_OVERVIEW_PATH, result -> {
            if (result.success) {
                readConsole(result.body, summary);
            } else {
                pending.captureFailure(result, "系统监控同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.AI_TOKEN_USAGE_OVERVIEW_PATH, result -> {
            if (result.success) {
                readToken(result.body, summary);
            } else {
                pending.captureFailure(result, "调用用量同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.MARKET_SYNC_LATEST_FULL_PATH, result -> {
            if (result.success && isApiSuccess(result.body)) {
                readSyncJobs(result.body, summary);
            } else {
                pending.captureFailure(result, "同步任务状态同步失败。");
            }
            pending.done();
        });
    }

    private void readConsole(String body, AdminSummary summary) {
        JSONObject root = object(body);
        summary.userCount = root.optJSONObject("user") == null ? 0 : root.optJSONObject("user").optLong("totalUserCount", 0);
        JSONObject visit = root.optJSONObject("visit");
        if (visit != null) {
            summary.totalVisitCount = visit.optLong("totalVisitCount", 0);
        }
        JSONObject tokenUsage = root.optJSONObject("tokenUsage");
        if (tokenUsage != null) {
            summary.tokenRequestCount = Math.max(summary.tokenRequestCount, tokenUsage.optLong("requestCount", 0));
        }
    }

    private void readToken(String body, AdminSummary summary) {
        JSONObject root = object(body);
        summary.tokenRequestCount = Math.max(summary.tokenRequestCount, root.optLong("requestCount", 0));
        JSONObject cost = root.optJSONObject("estimatedCost");
        if (cost != null) {
            summary.totalCost = cost.optDouble("totalCost", 0);
            summary.currency = cost.optString("currency", summary.currency);
        }
    }

    private void readSyncJobs(String body, AdminSummary summary) {
        JSONArray data = object(body).optJSONArray("data");
        if (data == null) {
            return;
        }
        summary.syncJobCount = data.length();
        JSONObject latest = data.optJSONObject(0);
        if (latest != null) {
            summary.latestSyncStatus = latest.optString("status", "");
            summary.latestSyncType = latest.optString("targetType", "");
            summary.latestSyncError = latest.optString("errorMessage", "");
        }
    }

    private boolean isApiSuccess(String body) {
        return object(body).optInt("code", -1) == 0;
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
        private final AdminSummary summary;
        private int remaining = 3;
        private ApiResult failure;

        private Pending(Callback callback, AdminSummary summary) {
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
            if (failure != null) {
                callback.onComplete(failure, summary);
            } else {
                callback.onComplete(ApiResult.success(200, "", "系统管理数据已同步。"), summary);
            }
        }
    }
}
