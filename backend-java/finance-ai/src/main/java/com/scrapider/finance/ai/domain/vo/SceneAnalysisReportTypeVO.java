package com.scrapider.finance.ai.domain.vo;

import com.scrapider.finance.domain.enums.SceneAnalysisReportTypeEnum;

public record SceneAnalysisReportTypeVO(
        String code,
        String label) {

    public static SceneAnalysisReportTypeVO fromEnum(SceneAnalysisReportTypeEnum type) {
        return new SceneAnalysisReportTypeVO(type.getCode(), type.getLabel());
    }
}
