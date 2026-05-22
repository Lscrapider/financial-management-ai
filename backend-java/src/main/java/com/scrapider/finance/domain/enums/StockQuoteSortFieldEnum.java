package com.scrapider.finance.domain.enums;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import lombok.Getter;

@Getter
public enum StockQuoteSortFieldEnum {
    STOCK_CODE("stockCode", StockQuoteSnapshotPO::getStockCode),
    LATEST_PRICE("latestPrice", StockQuoteSnapshotPO::getLatestPrice),
    CHANGE_PERCENT("changePercent", StockQuoteSnapshotPO::getChangePercent),
    VOLUME("volume", StockQuoteSnapshotPO::getVolume),
    TURNOVER_AMOUNT("turnoverAmount", StockQuoteSnapshotPO::getTurnoverAmount),
    TURNOVER_RATE("turnoverRate", StockQuoteSnapshotPO::getTurnoverRate),
    AMPLITUDE("amplitude", StockQuoteSnapshotPO::getAmplitude),
    TOTAL_MARKET_VALUE("totalMarketValue", StockQuoteSnapshotPO::getTotalMarketValue),
    SYNCED_AT("syncedAt", StockQuoteSnapshotPO::getSyncedAt);

    private final String code;
    private final SFunction<StockQuoteSnapshotPO, ?> column;

    StockQuoteSortFieldEnum(String code, SFunction<StockQuoteSnapshotPO, ?> column) {
        this.code = code;
        this.column = column;
    }

    public static StockQuoteSortFieldEnum of(String code) {
        for (StockQuoteSortFieldEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return CHANGE_PERCENT;
    }
}
