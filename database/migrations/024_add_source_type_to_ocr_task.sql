ALTER TABLE ocr_task
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'ocr';

COMMENT ON COLUMN ocr_task.source_type IS '任务来源类型：ocr、manual_text';

CREATE INDEX IF NOT EXISTS idx_ocr_task_source_type_submitted_at
    ON ocr_task (source_type, submitted_at DESC)
    WHERE deleted_at IS NULL;
