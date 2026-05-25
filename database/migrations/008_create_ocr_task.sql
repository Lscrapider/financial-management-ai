CREATE TABLE IF NOT EXISTS ocr_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ready',
    current_stage VARCHAR(64) NOT NULL DEFAULT 'document.normalize',
    progress INTEGER NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    segment_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ocr_task IS 'OCR 识别任务表';
COMMENT ON COLUMN ocr_task.task_no IS '任务编号';
COMMENT ON COLUMN ocr_task.original_filename IS '原始文件名';
COMMENT ON COLUMN ocr_task.stored_filename IS '存储文件名';
COMMENT ON COLUMN ocr_task.file_path IS '本地文件路径';
COMMENT ON COLUMN ocr_task.file_type IS '文件类型，例如 pdf、png、jpg';
COMMENT ON COLUMN ocr_task.content_type IS '上传文件 Content-Type';
COMMENT ON COLUMN ocr_task.file_size IS '文件大小，单位字节';
COMMENT ON COLUMN ocr_task.status IS '任务状态：ready、running、manual_review_required、finished、failed';
COMMENT ON COLUMN ocr_task.current_stage IS '当前处理阶段：document.normalize、ocr.recognize、text.clean、quality.validate、embedding.index';
COMMENT ON COLUMN ocr_task.progress IS '处理进度百分比';
COMMENT ON COLUMN ocr_task.page_count IS '扫描页数';
COMMENT ON COLUMN ocr_task.segment_count IS '文本分段数';
COMMENT ON COLUMN ocr_task.error_message IS '失败原因';
COMMENT ON COLUMN ocr_task.submitted_at IS '提交时间';
COMMENT ON COLUMN ocr_task.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_ocr_task_status_updated_at
    ON ocr_task (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ocr_task_submitted_at
    ON ocr_task (submitted_at DESC);
