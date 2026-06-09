package com.scrapider.finance.domain.enums;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum AiTokenUsageSourceEnum {

    AGENT("agent"),
    REPORT("report");

    private final String code;

    AiTokenUsageSourceEnum(String code) {
        this.code = code;
    }

    public static AiTokenUsageSourceEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
