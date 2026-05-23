package com.scrapider.finance.domain.enums;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import lombok.Getter;

@Getter
public enum IndexQuoteSortFieldEnum {
    INDEX_CODE("indexCode", IndexQuoteSnapshotPO::getIndexCode),
    LATEST_PRICE("latestPrice", IndexQuoteSnapshotPO::getLatestPrice),
    CHANGE_PERCENT("changePercent", IndexQuoteSnapshotPO::getChangePercent),
    VOLUME("volume", IndexQuoteSnapshotPO::getVolume),
    TURNOVER_AMOUNT("turnoverAmount", IndexQuoteSnapshotPO::getTurnoverAmount),
    AMPLITUDE("amplitude", IndexQuoteSnapshotPO::getAmplitude),
    SYNCED_AT("syncedAt", IndexQuoteSnapshotPO::getSyncedAt);

    private final String code;
    private final SFunction<IndexQuoteSnapshotPO, ?> column;

    IndexQuoteSortFieldEnum(String code, SFunction<IndexQuoteSnapshotPO, ?> column) {
        this.code = code;
        this.column = column;
    }

    public static IndexQuoteSortFieldEnum of(String code) {
        for (IndexQuoteSortFieldEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return INDEX_CODE;
    }
}
