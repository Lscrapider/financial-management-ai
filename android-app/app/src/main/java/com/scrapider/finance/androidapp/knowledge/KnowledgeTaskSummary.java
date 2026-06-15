package com.scrapider.finance.androidapp.knowledge;

public final class KnowledgeTaskSummary {
    public long ocrTotal;
    public long manualTotal;
    public int processingCount;
    public int reviewCount;
    public int completedCount;
    public int failedCount;
    public int segmentCount;
    public String latestTaskNo = "";
    public String latestFilename = "";
    public String latestStage = "";
    public String latestStatus = "";
    public int latestProgress;
    public String updatedAt = "--:--";

    public boolean hasAnyData() {
        return ocrTotal > 0 || manualTotal > 0 || processingCount > 0 || completedCount > 0 || failedCount > 0;
    }
}
