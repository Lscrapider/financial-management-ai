package com.scrapider.finance.androidapp.watch;

import com.scrapider.finance.androidapp.alert.AlertRules;

import org.json.JSONObject;

public final class WatchAlert {
    public final String type;
    public final String code;
    public final String name;
    public final boolean enabled;
    public final boolean emailNotification;
    public final boolean outOfThreshold;
    public final double thresholdPercent;
    public final double latestPrice;
    public final double changePercent;
    public final String lastAlertedAt;

    private WatchAlert(
            String type,
            String code,
            String name,
            boolean enabled,
            boolean emailNotification,
            boolean outOfThreshold,
            double thresholdPercent,
            double latestPrice,
            double changePercent,
            String lastAlertedAt) {
        this.type = type == null ? "" : type;
        this.code = code == null ? "" : code;
        this.name = name == null ? "未命名标的" : name;
        this.enabled = enabled;
        this.emailNotification = emailNotification;
        this.outOfThreshold = outOfThreshold;
        this.thresholdPercent = thresholdPercent;
        this.latestPrice = latestPrice;
        this.changePercent = changePercent;
        this.lastAlertedAt = lastAlertedAt == null ? "" : lastAlertedAt;
    }

    static WatchAlert from(JSONObject item) {
        return new WatchAlert(
                item.optString("targetType", ""),
                item.optString("stockCode", ""),
                item.optString("stockName", "未命名标的"),
                item.optBoolean("enabled", false),
                item.optBoolean("emailNotification", false),
                item.optBoolean("outOfThreshold", false),
                item.optDouble("thresholdPercent", 0),
                item.optDouble("latestPrice", 0),
                item.optDouble("changePercent", 0),
                item.optString("lastAlertedAt", ""));
    }

    boolean nearThreshold() {
        return !outOfThreshold && AlertRules.isNearThreshold(enabled, changePercent, thresholdPercent);
    }
}
