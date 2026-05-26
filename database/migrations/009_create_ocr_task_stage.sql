CREATE TABLE IF NOT EXISTS ocr_task_stage (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    input_message JSONB,
    output_message JSONB,
    input_ref JSONB,
    output_ref JSONB,
    metrics JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ocr_task_stage_task_stage UNIQUE (task_no, stage)
);

COMMENT ON TABLE ocr_task_stage IS 'OCR 任务阶段处理记录表';
COMMENT ON COLUMN ocr_task_stage.task_no IS '任务编号';
COMMENT ON COLUMN ocr_task_stage.stage IS '处理阶段';
COMMENT ON COLUMN ocr_task_stage.status IS '阶段状态：pending、running、finished、failed';
COMMENT ON COLUMN ocr_task_stage.attempt_count IS '当前阶段尝试次数';
COMMENT ON COLUMN ocr_task_stage.max_attempts IS '当前阶段最大尝试次数';
COMMENT ON COLUMN ocr_task_stage.input_message IS '阶段消费到的 RabbitMQ 消息体';
COMMENT ON COLUMN ocr_task_stage.output_message IS '阶段成功后发布到下一阶段的 RabbitMQ 消息体';
COMMENT ON COLUMN ocr_task_stage.input_ref IS '阶段输入产物引用';
COMMENT ON COLUMN ocr_task_stage.output_ref IS '阶段输出产物引用';
COMMENT ON COLUMN ocr_task_stage.metrics IS '阶段处理指标';
COMMENT ON COLUMN ocr_task_stage.error_message IS '阶段失败原因';

CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_no
    ON ocr_task_stage (task_no);

CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_status_updated_at
    ON ocr_task_stage (status, updated_at DESC);
