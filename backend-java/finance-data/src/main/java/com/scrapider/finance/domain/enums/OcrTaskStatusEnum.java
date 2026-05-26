package com.scrapider.finance.domain.enums;

import java.util.Arrays;
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

    public static OcrTaskStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported ocr task status: " + code));
    }
}
