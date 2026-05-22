package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum SortOrderEnum {
    ASC("asc", true),
    DESC("desc", false);

    private final String code;
    private final boolean asc;

    SortOrderEnum(String code, boolean asc) {
        this.code = code;
        this.asc = asc;
    }

    public static SortOrderEnum of(String code) {
        for (SortOrderEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return DESC;
    }
}
