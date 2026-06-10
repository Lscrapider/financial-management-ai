package com.scrapider.finance.domain.enums;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum AiTokenUsagePhaseEnum {

    PLANNING("planning", "规划"),
    INITIAL_PLANNING("initial_planning", "初始规划"),
    TOOL_FOLLOWUP_PLANNING("tool_followup_planning", "工具后续规划"),
    DIRECT_ANSWER("direct_answer", "直接回答"),
    TOOL_RESULT_ANSWER("tool_result_answer", "基于工具结果回答"),
    FINAL_ANSWER("final_answer", "最终整理回答"),
    REPORT_GENERATE("report_generate", "报告生成");

    private final String code;
    private final String label;

    AiTokenUsagePhaseEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AiTokenUsagePhaseEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
