package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum OcrReviewStatusEnum {
    PENDING("pending"),
    SAVED("saved"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String code;

    OcrReviewStatusEnum(String code) {
        this.code = code;
    }
}
