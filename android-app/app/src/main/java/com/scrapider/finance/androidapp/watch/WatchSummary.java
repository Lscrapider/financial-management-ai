package com.scrapider.finance.androidapp.watch;

import java.util.ArrayList;
import java.util.List;

public final class WatchSummary {
    public final List<String> groupIds = new ArrayList<>();
    public final List<String> groupNames = new ArrayList<>();
    public final List<Integer> groupItemCounts = new ArrayList<>();
    public final List<WatchItem> items = new ArrayList<>();
    public final List<WatchAlert> alerts = new ArrayList<>();
    public int outAlertCount;
    public int nearAlertCount;
    public int enabledAlertCount;
    public int emailAlertCount;
    public String latestAlertTime = "";
    public String updatedAt = "--:--";

    public boolean hasAnyData() {
        return !groupNames.isEmpty() || !items.isEmpty() || !alerts.isEmpty();
    }

    public WatchItem itemAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public WatchAlert alertAt(int index) {
        return index >= 0 && index < alerts.size() ? alerts.get(index) : null;
    }
}
