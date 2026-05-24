package com.scrapider.finance.domain.enums;

import java.util.Arrays;

public enum StockRankMarketEnum {

    ASHARE("ASHARE", "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"),
    SH_MAIN("SH_MAIN", "m:1+t:2"),
    SZ_MAIN("SZ_MAIN", "m:0+t:6"),
    STAR("STAR", "m:1+t:23"),
    CHINEXT("CHINEXT", "m:0+t:80");

    private final String code;
    private final String eastMoneyFilter;

    StockRankMarketEnum(String code, String eastMoneyFilter) {
        this.code = code;
        this.eastMoneyFilter = eastMoneyFilter;
    }

    public String getCode() {
        return code;
    }

    public String getEastMoneyFilter() {
        return eastMoneyFilter;
    }

    public static StockRankMarketEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ASHARE;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported market: " + code));
    }
}
