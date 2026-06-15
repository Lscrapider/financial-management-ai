package com.scrapider.finance.androidapp.market;

import org.json.JSONObject;

public final class MarketQuote {
    public final String name;
    public final String code;
    public final double latestPrice;
    public final double changePercent;
    public final double turnoverAmount;
    public final long volume;
    public final double openPrice;
    public final double highPrice;
    public final double lowPrice;
    public final double premiumRate;
    public final String rating;
    public final String syncedAt;

    private MarketQuote(
            String name,
            String code,
            double latestPrice,
            double changePercent,
            double turnoverAmount,
            long volume,
            double openPrice,
            double highPrice,
            double lowPrice,
            double premiumRate,
            String rating,
            String syncedAt) {
        this.name = name;
        this.code = code;
        this.latestPrice = latestPrice;
        this.changePercent = changePercent;
        this.turnoverAmount = turnoverAmount;
        this.volume = volume;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.premiumRate = premiumRate;
        this.rating = rating == null ? "" : rating;
        this.syncedAt = syncedAt == null ? "" : syncedAt;
    }

    static MarketQuote stock(JSONObject item) {
        return from(item, "stockName", "stockCode", "", "conversionPremiumRate");
    }

    static MarketQuote index(JSONObject item) {
        return from(item, "indexName", "indexCode", "", "conversionPremiumRate");
    }

    static MarketQuote bond(JSONObject item) {
        return from(item, "bondName", "bondCode", item.optString("bondRating", ""), "conversionPremiumRate");
    }

    private static MarketQuote from(JSONObject item, String nameKey, String codeKey, String rating, String premiumKey) {
        return new MarketQuote(
                item.optString(nameKey, "未命名"),
                item.optString(codeKey, "--"),
                item.optDouble("latestPrice", 0),
                item.optDouble("changePercent", 0),
                item.optDouble("turnoverAmount", 0),
                item.optLong("volume", 0),
                item.optDouble("openPrice", 0),
                item.optDouble("highPrice", 0),
                item.optDouble("lowPrice", 0),
                item.optDouble(premiumKey, 0),
                rating,
                item.optString("syncedAt", ""));
    }
}
