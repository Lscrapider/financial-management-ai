package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum SceneAnalysisTaskStatusEnum {
    PENDING("pending"),
    PROCESSING_CURRENT_SCENES("processing_current_scenes"),
    CURRENT_SCENES_READY("current_scenes_ready"),
    RETRIEVING_KNOWLEDGE("retrieving_knowledge"),
    GENERATING_REPORT("generating_report"),
    SUCCESS("success"),
    FAILED("failed");

    private final String code;

    SceneAnalysisTaskStatusEnum(String code) {
        this.code = code;
    }
}
