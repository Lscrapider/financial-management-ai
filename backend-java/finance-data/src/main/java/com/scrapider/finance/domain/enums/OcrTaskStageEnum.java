package com.scrapider.finance.domain.enums;

import lombok.Getter;

@Getter
public enum OcrTaskStageEnum {
    DOCUMENT_NORMALIZE("document.normalize"),
    OCR_RECOGNIZE("ocr.recognize"),
    TEXT_CLEAN("text.clean"),
    QUALITY_VALIDATE("quality.validate"),
    CHUNK_TAG_RULE("chunk.tag.rule"),
    CHUNK_TAG_LLM("chunk.tag.llm"),
    CHUNK_TAG_CORRECT("chunk.tag.correct"),
    EMBEDDING_INDEX("embedding.index");

    private final String code;

    OcrTaskStageEnum(String code) {
        this.code = code;
    }
}
