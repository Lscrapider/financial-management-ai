package com.scrapider.finance.androidapp.research;

public final class ReportDetailSummary {
    public long reportId;
    public String taskNo = "";
    public String targetType = "";
    public String targetCode = "";
    public String targetName = "暂无报告标的";
    public String reportType = "";
    public String generationType = "";
    public int versionNo;
    public String status = "";
    public String reportText = "";
    public String model = "";
    public String errorMessage = "";
    public String generatedAt = "";
    public String createdAt = "";
    public int referenceCount;

    public boolean hasAnyData() {
        return reportId > 0 || !reportText.isEmpty();
    }

    public String displayTime() {
        String value = generatedAt.isEmpty() ? createdAt : generatedAt;
        if (value.length() >= 16) {
            return value.substring(11, 16);
        }
        return value.isEmpty() ? "--:--" : value;
    }
}
