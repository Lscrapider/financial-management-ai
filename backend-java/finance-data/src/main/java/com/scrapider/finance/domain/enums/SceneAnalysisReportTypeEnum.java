package com.scrapider.finance.domain.enums;

import java.util.Arrays;

public enum SceneAnalysisReportTypeEnum {

    QUICK_ANALYSIS("quick_analysis", "快速分析"),
    RISK_CHECK("risk_check", "风险检查"),
    VALUATION_REPORT("valuation_report", "估值报告");

    private final String code;
    private final String label;

    SceneAnalysisReportTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return this.code;
    }

    public String getLabel() {
        return this.label;
    }

    public static SceneAnalysisReportTypeEnum of(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported reportType: " + code));
    }
}
