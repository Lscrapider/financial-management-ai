package com.scrapider.finance.androidapp.research;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ReportDetailRepository {
    public interface Callback {
        void onComplete(ApiResult result, ReportDetailSummary summary);
    }

    private final ApiClient apiClient;

    public ReportDetailRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void loadLatest(Callback callback) {
        apiClient.get(ApiConfig.REPORT_TARGETS_LATEST_PATH, result -> {
            if (!result.success) {
                callback.onComplete(result, new ReportDetailSummary());
                return;
            }
            long reportId = latestReportId(result.body);
            if (reportId <= 0) {
                callback.onComplete(ApiResult.success(200, "", "暂无可展示的报告详情。"), new ReportDetailSummary());
                return;
            }
            apiClient.get(ApiConfig.REPORT_DETAIL_PATH_PREFIX + reportId, detailResult -> {
                ReportDetailSummary summary = new ReportDetailSummary();
                if (detailResult.success) {
                    readDetail(detailResult.body, summary);
                }
                callback.onComplete(detailResult, summary);
            });
        });
    }

    private long latestReportId(String body) {
        JSONArray records = object(body).optJSONArray("records");
        if (records == null) {
            return 0;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item != null) {
                long reportId = item.optLong("latestReportId", 0);
                if (reportId > 0) {
                    return reportId;
                }
            }
        }
        return 0;
    }

    private void readDetail(String body, ReportDetailSummary summary) {
        JSONObject root = object(body);
        summary.reportId = root.optLong("reportId", 0);
        summary.taskNo = root.optString("taskNo", "");
        summary.targetType = root.optString("targetType", "");
        summary.targetCode = root.optString("targetCode", "");
        summary.targetName = root.optString("targetName", "暂无报告标的");
        summary.reportType = root.optString("reportType", "");
        summary.generationType = root.optString("generationType", "");
        summary.versionNo = root.optInt("versionNo", 0);
        summary.status = root.optString("status", "");
        summary.reportText = root.optString("reportText", "");
        summary.model = root.optString("model", "");
        summary.errorMessage = root.optString("errorMessage", "");
        summary.generatedAt = root.optString("generatedAt", "");
        summary.createdAt = root.optString("createdAt", "");
        summary.referenceCount = countOccurrences(summary.reportText, "引用：")
                + countOccurrences(root.optString("reportContent", ""), "chunkIds");
    }

    private int countOccurrences(String value, String token) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = value.indexOf(token);
        while (index >= 0) {
            count++;
            index = value.indexOf(token, index + token.length());
        }
        return count;
    }

    private JSONObject object(String body) {
        try {
            return new JSONObject(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }
}
