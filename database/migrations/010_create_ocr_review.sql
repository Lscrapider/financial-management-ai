CREATE TABLE IF NOT EXISTS ocr_review (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    cleaned_ref JSONB NOT NULL,
    reviewed_ref JSONB,
    draft_content JSONB,
    overall_confidence NUMERIC(6, 4),
    paragraph_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    reviewer_id BIGINT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ocr_review IS 'OCR 人工复核任务表';
COMMENT ON COLUMN ocr_review.task_no IS '任务编号';
COMMENT ON COLUMN ocr_review.status IS '复核状态：pending、saved、approved、rejected';
COMMENT ON COLUMN ocr_review.cleaned_ref IS '文本清洗阶段 cleaned.json 对象存储引用';
COMMENT ON COLUMN ocr_review.reviewed_ref IS '人工确认后 reviewed.json 对象存储引用';
COMMENT ON COLUMN ocr_review.draft_content IS '人工复核草稿内容';
COMMENT ON COLUMN ocr_review.overall_confidence IS '整体置信度';
COMMENT ON COLUMN ocr_review.paragraph_count IS '段落数量';
COMMENT ON COLUMN ocr_review.warning_count IS '警告数量';
COMMENT ON COLUMN ocr_review.reviewer_id IS '复核人 ID';
COMMENT ON COLUMN ocr_review.reviewed_at IS '确认复核时间';

CREATE INDEX IF NOT EXISTS idx_ocr_review_status_updated_at
    ON ocr_review (status, updated_at DESC);
