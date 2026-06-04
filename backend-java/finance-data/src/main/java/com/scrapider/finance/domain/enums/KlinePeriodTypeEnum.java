package com.scrapider.finance.domain.enums;

public enum KlinePeriodTypeEnum {

    DAILY("daily", "day"),
    WEEKLY("weekly", "week"),
    MONTHLY("monthly", "month");

    private final String code;
    private final String tencentCode;

    KlinePeriodTypeEnum(String code, String tencentCode) {
        this.code = code;
        this.tencentCode = tencentCode;
    }

    public String getCode() {
        return this.code;
    }

    public String getTencentCode() {
        return this.tencentCode;
    }
}
