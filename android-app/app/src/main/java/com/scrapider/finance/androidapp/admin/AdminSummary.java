package com.scrapider.finance.androidapp.admin;

public final class AdminSummary {
    public long totalVisitCount;
    public long userCount;
    public long tokenRequestCount;
    public double totalCost;
    public String currency = "CNY";
    public int syncJobCount;
    public String latestSyncStatus = "";
    public String latestSyncType = "";
    public String latestSyncError = "";

    public boolean hasAnyData() {
        return totalVisitCount > 0 || userCount > 0 || tokenRequestCount > 0 || syncJobCount > 0;
    }
}
