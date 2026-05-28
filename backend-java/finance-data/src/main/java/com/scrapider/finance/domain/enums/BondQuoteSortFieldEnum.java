package com.scrapider.finance.domain.enums;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import lombok.Getter;

@Getter
public enum BondQuoteSortFieldEnum {
    BOND_CODE("bondCode", BondQuoteSnapshotPO::getBondCode),
    LATEST_PRICE("latestPrice", BondQuoteSnapshotPO::getLatestPrice),
    CHANGE_PERCENT("changePercent", BondQuoteSnapshotPO::getChangePercent),
    VOLUME("volume", BondQuoteSnapshotPO::getVolume),
    TURNOVER_AMOUNT("turnoverAmount", BondQuoteSnapshotPO::getTurnoverAmount),
    AMPLITUDE("amplitude", BondQuoteSnapshotPO::getAmplitude),
    SYNCED_AT("syncedAt", BondQuoteSnapshotPO::getSyncedAt);

    private final String code;
    private final SFunction<BondQuoteSnapshotPO, ?> column;

    BondQuoteSortFieldEnum(String code, SFunction<BondQuoteSnapshotPO, ?> column) {
        this.code = code;
        this.column = column;
    }

    public static BondQuoteSortFieldEnum of(String code) {
        for (BondQuoteSortFieldEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return BOND_CODE;
    }
}
