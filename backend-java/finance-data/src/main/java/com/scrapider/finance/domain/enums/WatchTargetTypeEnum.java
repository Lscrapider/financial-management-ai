package com.scrapider.finance.domain.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Arrays;

public enum WatchTargetTypeEnum {
    STOCK,
    INDEX,
    BOND,
    FUND,
    SECTOR;

    public static WatchTargetTypeEnum of(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("targetType must not be blank.");
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported targetType: " + value));
    }
}
