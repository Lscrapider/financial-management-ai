package com.scrapider.finance.androidapp.workbench;

import com.scrapider.finance.androidapp.alert.AlertRules;
import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WorkbenchRepository {
    public interface Callback {
        void onComplete(ApiResult result, WorkbenchSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);
    private final ApiClient apiClient;

    public WorkbenchRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        WorkbenchSummary summary = new WorkbenchSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.get(ApiConfig.WATCH_GROUPS_PATH, result -> {
            if (result.success && isApiSuccess(result.body)) {
                readWatchGroups(result.body, summary);
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
        apiClient.get(ApiConfig.REPORT_TARGETS_PATH, result -> {
            if (result.success) {
                readReportTargets(result.body, summary);
            } else {
                pending.captureFailure(result, "报告动态同步失败。");
            }
            pending.done();
        });
    }

    private void readWatchGroups(String body, WorkbenchSummary summary) {
        JSONArray groups = dataArray(body);
        summary.watchGroupCount = groups.length();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            JSONArray items = group == null ? new JSONArray() : group.optJSONArray("items");
            if (items == null) {
                continue;
            }
            summary.watchItemCount += items.length();
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) {
                    continue;
                }
                String targetType = item.optString("targetType", "");
                countTargetType(summary, targetType);
                double changePercent = item.optDouble("changePercent", 0);
                if (changePercent > 0) {
                    summary.watchUpCount++;
                } else if (changePercent < 0) {
                    summary.watchDownCount++;
                }
                summary.movements.add(new WorkbenchMovement(
                        targetType,
                        item.optString("targetCode", ""),
                        item.optString("targetName", "未命名标的"),
                        item.optDouble("latestPrice", 0),
                        changePercent));
            }
        }
    }

    private void countTargetType(WorkbenchSummary summary, String targetType) {
        if ("STOCK".equals(targetType)) {
            summary.stockItemCount++;
        } else if ("INDEX".equals(targetType)) {
            summary.indexItemCount++;
        } else if ("BOND".equals(targetType)) {
            summary.bondItemCount++;
        }
    }

    private void readAlerts(String body, WorkbenchSummary summary) {
        JSONArray alerts = dataArray(body);
        summary.alertCount = alerts.length();
        for (int i = 0; i < alerts.length(); i++) {
            JSONObject alert = alerts.optJSONObject(i);
            if (alert == null) {
                continue;
            }
            if (alert.optBoolean("enabled", false)) {
                summary.enabledAlertCount++;
            }
            boolean outOfThreshold = alert.optBoolean("outOfThreshold", false);
            if (outOfThreshold) {
                summary.outAlertCount++;
            } else if (isNearThreshold(alert)) {
                summary.nearAlertCount++;
            }
        }
    }

    private void readReportTargets(String body, WorkbenchSummary summary) {
        JSONObject root = object(body);
        summary.reportTotal = root.optInt("total", 0);
        JSONArray records = root.optJSONArray("records");
        if (records == null) {
            return;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String status = item.optString("latestStatus", "");
            if ("failed".equals(status)) {
                summary.reportFailedCount++;
            } else if (isGeneratingStatus(status)) {
                summary.reportGeneratingCount++;
            }
            if (summary.reportLines.size() < 4) {
                summary.reportLines.add(reportLine(item));
            }
        }
    }

    private WorkbenchReportLine reportLine(JSONObject item) {
        String name = reportName(item);
        String status = statusLabel(item.optString("latestStatus", ""));
        String time = reportTime(item);
        String text = name + " " + status + (time.isEmpty() ? "" : " · " + time);
        return new WorkbenchReportLine(text, statusTone(item.optString("latestStatus", "")));
    }

    private String reportName(JSONObject item) {
        String targetName = item.optString("targetName", "");
        if (!targetName.isEmpty()) {
            return targetName;
        }
        String targetCode = item.optString("targetCode", "");
        return targetCode.isEmpty() ? "未命名标的" : targetCode;
    }

    private String reportTime(JSONObject item) {
        String generatedAt = item.optString("latestGeneratedAt", "");
        String createdAt = item.optString("latestCreatedAt", "");
        String time = generatedAt.isEmpty() ? createdAt : generatedAt;
        if (time == null || time.isEmpty()) {
            return "";
        }
        time = time.replace('T', ' ').trim();
        return time.length() >= 16 ? time.substring(0, 16) : time;
    }

    private String statusLabel(String status) {
        if ("success".equals(status)) {
            return "已完成";
        }
        if ("failed".equals(status)) {
            return "失败";
        }
        if (status == null || status.isEmpty()) {
            return "暂无状态";
        }
        return "处理中";
    }

    private String statusTone(String status) {
        if ("success".equals(status)) {
            return "blue";
        }
        if ("failed".equals(status)) {
            return "danger";
        }
        if (isGeneratingStatus(status)) {
            return "amber";
        }
        return "muted";
    }

    private boolean isNearThreshold(JSONObject alert) {
        if (!alert.optBoolean("enabled", false)) {
            return false;
        }
        return AlertRules.isNearThreshold(
                alert.optBoolean("enabled", false),
                alert.optDouble("changePercent", 0),
                alert.optDouble("thresholdPercent", 0));
    }

    private boolean isGeneratingStatus(String status) {
        return "pending".equals(status)
                || "processing_current_scenes".equals(status)
                || "current_scenes_ready".equals(status)
                || "retrieving_knowledge".equals(status)
                || "generating_report".equals(status);
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
        private final WorkbenchSummary summary;
        private int remaining = 3;
        private ApiResult failure;

        private Pending(Callback callback, WorkbenchSummary summary) {
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
            summary.finish();
            if (failure != null) {
                callback.onComplete(failure, summary);
            } else {
                callback.onComplete(ApiResult.success(200, "", "工作台后端数据已同步。"), summary);
            }
        }
    }
}
