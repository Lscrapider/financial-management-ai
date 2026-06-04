package com.scrapider.finance.domain.enums;

public enum KlineAdjustTypeEnum {

    HFQ("hfq"),
    QFQ("qfq"),
    NONE("none");

    private final String code;

    KlineAdjustTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
