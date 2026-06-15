package com.scrapider.finance.androidapp.research;

import org.json.JSONObject;

public final class ResearchReportTarget {
    public final String type;
    public final String code;
    public final String name;
    public final long latestReportId;
    public final String latestTaskNo;
    public final String status;
    public final String reportType;
    public final int versionNo;
    public final String preview;
    public final String generatedAt;
    public final String createdAt;
    public final long reportCount;

    private ResearchReportTarget(
            String type,
            String code,
            String name,
            long latestReportId,
            String latestTaskNo,
            String status,
            String reportType,
            int versionNo,
            String preview,
            String generatedAt,
            String createdAt,
            long reportCount) {
        this.type = type == null ? "" : type;
        this.code = code == null ? "" : code;
        this.name = name == null ? "未命名标的" : name;
        this.latestReportId = latestReportId;
        this.latestTaskNo = latestTaskNo == null ? "" : latestTaskNo;
        this.status = status == null ? "" : status;
        this.reportType = reportType == null ? "" : reportType;
        this.versionNo = versionNo;
        this.preview = preview == null ? "" : preview;
        this.generatedAt = generatedAt == null ? "" : generatedAt;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.reportCount = reportCount;
    }

    static ResearchReportTarget from(JSONObject item) {
        return new ResearchReportTarget(
                item.optString("targetType", ""),
                item.optString("targetCode", ""),
                item.optString("targetName", "未命名标的"),
                item.optLong("latestReportId", 0),
                item.optString("latestTaskNo", ""),
                item.optString("latestStatus", ""),
                item.optString("latestReportType", ""),
                item.optInt("latestVersionNo", 0),
                item.optString("latestReportPreview", ""),
                item.optString("latestGeneratedAt", ""),
                item.optString("latestCreatedAt", ""),
                item.optLong("reportCount", 0));
    }

    String displayTime() {
        String value = generatedAt.isEmpty() ? createdAt : generatedAt;
        if (value.length() >= 16) {
            return value.substring(11, 16);
        }
        return value;
    }
}
