package com.scrapider.finance.androidapp.research;

import java.util.ArrayList;
import java.util.List;

public final class ResearchSummary {
    public final List<ResearchReportTarget> targets = new ArrayList<>();
    public long total;
    public int stockCount;
    public int indexCount;
    public int bondCount;
    public int pendingCount;
    public int retrievingCount;
    public int generatingCount;
    public int successCount;
    public int failedCount;
    public String updatedAt = "--:--";

    public boolean hasAnyData() {
        return total > 0 || !targets.isEmpty();
    }

    public int runningCount() {
        return pendingCount + retrievingCount + generatingCount;
    }

    public ResearchReportTarget targetAt(int index) {
        return index >= 0 && index < targets.size() ? targets.get(index) : null;
    }
}
