package com.scrapider.finance.androidapp.market;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeFormatters;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class MarketScreenBinder {
    private static final String SCREEN = "行情中心";

    public void apply(RuntimeValueStore runtimeValueStore, MarketSummary summary) {
        runtimeValueStore.put(SCREEN, "股票", summary.stocks.size() + " 条");
        runtimeValueStore.put(SCREEN, "指数", summary.indices.size() + " 条");
        runtimeValueStore.put(SCREEN, "可转债", summary.bonds.size() + " 条");
        runtimeValueStore.put(SCREEN, "沪深", "已同步 "
                + (summary.stocks.size() + summary.indices.size() + summary.bonds.size()) + " 条");
        runtimeValueStore.put(SCREEN, "更新时间", summary.updatedAt);

        applyQuoteRow(runtimeValueStore, "贵州茅台 600519", summary.stocks.size() > 0 ? summary.stocks.get(0) : null, false);
        applyQuoteRow(runtimeValueStore, "招商银行 600036", summary.stocks.size() > 1 ? summary.stocks.get(1) : null, false);
        applyQuoteRow(runtimeValueStore, "宁转债 123456", summary.firstBond(), true);

        MarketQuote selected = summary.selectedStock();
        if (selected == null) {
            selected = summary.firstIndex() == null ? summary.firstBond() : summary.firstIndex();
        }
        if (selected != null) {
            runtimeValueStore.put(SCREEN, "最新价", RuntimeFormatters.price(selected.latestPrice));
            runtimeValueStore.put(SCREEN, "涨跌幅", RuntimeFormatters.percent(selected.changePercent));
            runtimeValueStore.put(SCREEN, "成交量", RuntimeFormatters.volume(selected.volume));
            runtimeValueStore.put(SCREEN, "成交额", RuntimeFormatters.amount(selected.turnoverAmount));
            runtimeValueStore.put(SCREEN, "盘口", "今开 " + RuntimeFormatters.price(selected.openPrice)
                    + "，最高 " + RuntimeFormatters.price(selected.highPrice)
                    + "，最低 " + RuntimeFormatters.price(selected.lowPrice));
        }
        MarketQuote index = summary.firstIndex();
        if (index != null) {
            runtimeValueStore.put(SCREEN, "指数跳转", index.name + " " + index.code
                    + "，点位 " + RuntimeFormatters.price(index.latestPrice)
                    + "，涨跌 " + RuntimeFormatters.percent(index.changePercent));
        }
        MarketQuote bond = summary.firstBond();
        if (bond != null) {
            String rating = bond.rating.isEmpty() ? "暂无评级" : bond.rating;
            runtimeValueStore.put(SCREEN, "可转债字段", "评级 " + rating
                    + "，转股溢价率 " + RuntimeFormatters.percent(bond.premiumRate));
        }
    }

    public String statusMessage(ApiResult result, MarketSummary summary) {
        if (result.success) {
            return "行情中心已同步：股票 " + summary.stocks.size()
                    + " 条，指数 " + summary.indices.size()
                    + " 条，可转债 " + summary.bonds.size() + " 条。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用报价和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private void applyQuoteRow(RuntimeValueStore runtimeValueStore, String originalLabel, MarketQuote quote, boolean bond) {
        if (quote == null) {
            return;
        }
        runtimeValueStore.putLabel(SCREEN, originalLabel, quote.name + " " + quote.code);
        String value = RuntimeFormatters.price(quote.latestPrice) + "  "
                + RuntimeFormatters.percent(quote.changePercent);
        if (bond) {
            value = value + "  溢价率 " + RuntimeFormatters.percent(quote.premiumRate);
        }
        runtimeValueStore.put(SCREEN, originalLabel, value);
    }
}
