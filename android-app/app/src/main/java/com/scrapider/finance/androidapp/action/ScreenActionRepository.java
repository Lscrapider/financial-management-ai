package com.scrapider.finance.androidapp.action;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ScreenActionRepository {
    public interface Callback {
        void onComplete(ActionResult result);
    }

    private final ApiClient apiClient;

    public ScreenActionRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void checkAlerts(Callback callback) {
        apiClient.postJson(ApiConfig.STOCK_ALERT_CHECK_PATH, new JSONObject(), result -> {
            if (result.success && isApiSuccess(result.body)) {
                callback.onComplete(ActionResult.success(result.statusCode, "布控提醒手动检查已触发。", ""));
                return;
            }
            callback.onComplete(ActionResult.failure(result.statusCode, failureMessage(result, "布控提醒手动检查失败。")));
        });
    }

    public void saveWatchItem(JSONObject payload, Callback callback) {
        apiClient.postJson(ApiConfig.WATCH_ITEMS_PATH, payload, result -> {
            if (isSuccessfulMutation(result)) {
                callback.onComplete(ActionResult.success(result.statusCode, "观察池标的已保存。", ""));
                return;
            }
            callback.onComplete(ActionResult.failure(result.statusCode, failureMessage(result, "观察池标的保存失败。")));
        });
    }

    public void saveWatchGroup(String groupName, Callback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("groupName", groupName == null ? "" : groupName.trim());
        } catch (JSONException exception) {
            callback.onComplete(ActionResult.failure(0, "观察分组参数组装失败。"));
            return;
        }
        apiClient.postJson(ApiConfig.WATCH_GROUPS_PATH, payload, result -> {
            if (isSuccessfulMutation(result)) {
                String groupId = idFromBody(result.body);
                if (groupId.isEmpty()) {
                    callback.onComplete(ActionResult.failure(result.statusCode, "观察分组已提交，但后端未返回分组ID。"));
                    return;
                }
                callback.onComplete(ActionResult.success(result.statusCode, "观察分组已创建。", groupId));
                return;
            }
            callback.onComplete(ActionResult.failure(result.statusCode, failureMessage(result, "观察分组创建失败。")));
        });
    }

    public void saveStockAlert(JSONObject payload, Callback callback) {
        apiClient.postJson(ApiConfig.STOCK_ALERTS_PATH, payload, result -> {
            if (isSuccessfulMutation(result)) {
                callback.onComplete(ActionResult.success(result.statusCode, "布控提醒已保存。", ""));
                return;
            }
            callback.onComplete(ActionResult.failure(result.statusCode, failureMessage(result, "布控提醒保存失败。")));
        });
    }

    public void regenerateLatestReport(Callback callback) {
        apiClient.get(ApiConfig.REPORT_TARGETS_LATEST_PATH, result -> {
            if (!result.success) {
                callback.onComplete(ActionResult.failure(result.statusCode, result.message));
                return;
            }
            String taskNo = latestTaskNo(result.body);
            if (taskNo.isEmpty()) {
                callback.onComplete(ActionResult.failure(result.statusCode, "暂无可重新生成的报告任务。"));
                return;
            }
            apiClient.postJson("/api/ai/scene-analysis/tasks/" + taskNo + ApiConfig.REPORT_REGENERATE_PATH_SUFFIX,
                    new JSONObject(),
                    postResult -> {
                        if (postResult.success) {
                            callback.onComplete(ActionResult.success(postResult.statusCode, "报告重新生成已提交。", taskNo));
                            return;
                        }
                        callback.onComplete(ActionResult.failure(postResult.statusCode, failureMessage(postResult, "报告重新生成失败。")));
                    });
        });
    }

    public void submitKnowledgeMaterial(String queryText, int totalChunks, Callback callback) {
        String query = queryText == null ? "" : queryText.trim();
        if (query.isEmpty()) {
            callback.onComplete(ActionResult.failure(0, "请输入自然语言检索问题后再提交。"));
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("searchMode", "natural_language");
            payload.put("queryText", query);
            payload.put("totalChunks", Math.max(1, totalChunks));
        } catch (JSONException exception) {
            callback.onComplete(ActionResult.failure(0, "知识材料检索参数组装失败。"));
            return;
        }
        apiClient.postJson(ApiConfig.KNOWLEDGE_MATERIAL_TASKS_PATH, payload, result -> {
            if (result.success) {
                String taskNo = object(result.body).optString("taskNo", "");
                callback.onComplete(ActionResult.success(
                        result.statusCode,
                        taskNo.isEmpty() ? "知识材料检索任务已提交。" : "知识材料检索任务已提交：" + taskNo + "。",
                        taskNo));
                return;
            }
            callback.onComplete(ActionResult.failure(result.statusCode, failureMessage(result, "知识材料检索任务提交失败。")));
        });
    }

    private String latestTaskNo(String body) {
        JSONArray records = object(body).optJSONArray("records");
        if (records == null) {
            return "";
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item != null) {
                String taskNo = item.optString("latestTaskNo", "");
                if (!taskNo.isEmpty()) {
                    return taskNo;
                }
            }
        }
        return "";
    }

    private String idFromBody(String body) {
        JSONObject root = object(body);
        String direct = root.optString("id", "");
        if (!direct.isEmpty()) {
            return direct;
        }
        JSONObject data = root.optJSONObject("data");
        return data == null ? "" : data.optString("id", "");
    }

    private boolean isApiSuccess(String body) {
        return object(body).optInt("code", -1) == 0;
    }

    private boolean isSuccessfulMutation(ApiResult result) {
        if (!result.success) {
            return false;
        }
        JSONObject root = object(result.body);
        return !root.has("code") || root.optInt("code", -1) == 0;
    }

    private String failureMessage(ApiResult result, String fallback) {
        JSONObject root = object(result.body);
        String error = root.optString("error", "");
        String message = root.optString("message", "");
        if (!error.isEmpty()) {
            return error;
        }
        if (!message.isEmpty() && !"ok".equals(message)) {
            return message;
        }
        return result.message == null || result.message.isEmpty() ? fallback : result.message;
    }

    private JSONObject object(String body) {
        try {
            return new JSONObject(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }
}
