package com.scrapider.finance.androidapp.watch;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WatchRepository {
    public interface Callback {
        void onComplete(ApiResult result, WatchSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private final ApiClient apiClient;

    public WatchRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        WatchSummary summary = new WatchSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH, result -> {
            if (result.success && isApiSuccess(result.body)) {
                readGroups(result.body, summary);
            } else {
                pending.captureFailure(result, "观察池同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.STOCK_ALERTS_PATH, result -> {
            if (result.success && isApiSuccess(result.body)) {
                readAlerts(result.body, summary);
            } else {
                pending.captureFailure(result, "布控提醒同步失败。");
            }
            pending.done();
        });
    }

    private void readGroups(String body, WatchSummary summary) {
        JSONArray groups = dataArray(body);
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) {
                continue;
            }
            JSONArray items = group.optJSONArray("items");
            int itemCount = items == null ? 0 : items.length();
            summary.groupIds.add(group.optString("id", ""));
            summary.groupNames.add(group.optString("groupName", "未命名分组"));
            summary.groupItemCounts.add(itemCount);
            if (items == null) {
                continue;
            }
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item != null) {
                    summary.items.add(WatchItem.from(item));
                }
            }
        }
    }

    private void readAlerts(String body, WatchSummary summary) {
        JSONArray alerts = dataArray(body);
        for (int i = 0; i < alerts.length(); i++) {
            JSONObject item = alerts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            WatchAlert alert = WatchAlert.from(item);
            summary.alerts.add(alert);
            if (alert.enabled) {
                summary.enabledAlertCount++;
            }
            if (alert.emailNotification) {
                summary.emailAlertCount++;
            }
            if (alert.outOfThreshold) {
                summary.outAlertCount++;
            } else if (alert.nearThreshold()) {
                summary.nearAlertCount++;
            }
            if (!alert.lastAlertedAt.isEmpty()
                    && (summary.latestAlertTime.isEmpty() || alert.lastAlertedAt.compareTo(summary.latestAlertTime) > 0)) {
                summary.latestAlertTime = alert.lastAlertedAt;
            }
        }
    }

    private JSONArray dataArray(String body) {
        JSONObject root = object(body);
        JSONArray data = root.optJSONArray("data");
        return data == null ? new JSONArray() : data;
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
        private final WatchSummary summary;
        private int remaining = 2;
        private ApiResult failure;

        private Pending(Callback callback, WatchSummary summary) {
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
                callback.onComplete(ApiResult.success(200, "", "观察风控后端数据已同步。"), summary);
            }
        }
    }
}
