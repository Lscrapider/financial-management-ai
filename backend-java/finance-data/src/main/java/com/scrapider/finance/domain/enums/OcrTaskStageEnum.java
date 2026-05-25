package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum OcrTaskStageEnum {
    DOCUMENT_NORMALIZE("document.normalize"),
    OCR_RECOGNIZE("ocr.recognize"),
    TEXT_CLEAN("text.clean"),
    QUALITY_VALIDATE("quality.validate"),
    EMBEDDING_INDEX("embedding.index");

    private final String code;

    OcrTaskStageEnum(String code) {
        this.code = code;
    }
}
