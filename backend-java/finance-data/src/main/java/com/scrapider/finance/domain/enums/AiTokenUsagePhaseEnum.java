package com.scrapider.finance.domain.enums;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum AiTokenUsagePhaseEnum {

    PLANNING("planning"),
    FINAL_ANSWER("final_answer"),
    REPORT_GENERATE("report_generate");

    private final String code;

    AiTokenUsagePhaseEnum(String code) {
        this.code = code;
    }

    public static AiTokenUsagePhaseEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
