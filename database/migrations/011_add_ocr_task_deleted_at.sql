ALTER TABLE ocr_task
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

COMMENT ON COLUMN ocr_task.deleted_at IS '软删除时间，非空表示任务已从业务列表移除';

CREATE INDEX IF NOT EXISTS idx_ocr_task_deleted_status_submitted_at
    ON ocr_task (deleted_at, status, submitted_at DESC);
