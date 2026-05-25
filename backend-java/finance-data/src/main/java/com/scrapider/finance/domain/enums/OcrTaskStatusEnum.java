package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum OcrTaskStatusEnum {
    READY("ready"),
    RUNNING("running"),
    MANUAL_REVIEW_REQUIRED("manual_review_required"),
    FINISHED("finished"),
    FAILED("failed");

    private final String code;

    OcrTaskStatusEnum(String code) {
        this.code = code;
    }
}
