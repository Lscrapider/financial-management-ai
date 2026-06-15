package com.scrapider.finance.androidapp.alert;

public final class AlertRules {
    public static final double NEAR_THRESHOLD_RATIO = 0.8d;

    private AlertRules() {
    }

    public static boolean isNearThreshold(boolean enabled, double changePercent, double thresholdPercent) {
        return enabled
                && thresholdPercent > 0
                && Math.abs(changePercent) / Math.abs(thresholdPercent) >= NEAR_THRESHOLD_RATIO;
    }
}
