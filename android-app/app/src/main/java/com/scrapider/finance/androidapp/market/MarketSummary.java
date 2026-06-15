package com.scrapider.finance.androidapp.market;

import java.util.ArrayList;
import java.util.List;

public final class MarketSummary {
    public final List<MarketQuote> stocks = new ArrayList<>();
    public final List<MarketQuote> indices = new ArrayList<>();
    public final List<MarketQuote> bonds = new ArrayList<>();
    public String updatedAt = "--:--";

    public boolean hasAnyData() {
        return !stocks.isEmpty() || !indices.isEmpty() || !bonds.isEmpty();
    }

    public MarketQuote selectedStock() {
        return stocks.isEmpty() ? null : stocks.get(0);
    }

    public MarketQuote firstIndex() {
        return indices.isEmpty() ? null : indices.get(0);
    }

    public MarketQuote firstBond() {
        return bonds.isEmpty() ? null : bonds.get(0);
    }
}
