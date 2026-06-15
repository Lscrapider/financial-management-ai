package com.scrapider.finance.androidapp.watch;

import org.json.JSONObject;

public final class WatchItem {
    public final String type;
    public final String code;
    public final String name;
    public final String remark;
    public final double buyPrice;
    public final double position;
    public final double latestPrice;
    public final double changePercent;

    private WatchItem(
            String type,
            String code,
            String name,
            String remark,
            double buyPrice,
            double position,
            double latestPrice,
            double changePercent) {
        this.type = type == null ? "" : type;
        this.code = code == null ? "" : code;
        this.name = name == null ? "未命名标的" : name;
        this.remark = remark == null ? "" : remark;
        this.buyPrice = buyPrice;
        this.position = position;
        this.latestPrice = latestPrice;
        this.changePercent = changePercent;
    }

    static WatchItem from(JSONObject item) {
        return new WatchItem(
                item.optString("targetType", ""),
                item.optString("targetCode", ""),
                item.optString("targetName", "未命名标的"),
                item.optString("remark", ""),
                item.optDouble("buyPrice", 0),
                item.optDouble("position", 0),
                item.optDouble("latestPrice", 0),
                item.optDouble("changePercent", 0));
    }

    boolean hasPositionCost() {
        return buyPrice > 0 && position > 0 && latestPrice > 0;
    }

    double profit() {
        return (latestPrice - buyPrice) * position;
    }
}
