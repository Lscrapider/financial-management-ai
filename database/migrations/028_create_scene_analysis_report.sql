CREATE TABLE IF NOT EXISTS scene_analysis_report (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    generation_type VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'generating_report',
    report_content JSONB,
    report_text TEXT,
    model VARCHAR(100),
    error_message TEXT,
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scene_analysis_report_task
        FOREIGN KEY (task_id) REFERENCES scene_analysis_task(id),
    CONSTRAINT uk_scene_analysis_report_task_version
        UNIQUE (task_id, version_no)
);

COMMENT ON TABLE scene_analysis_report IS '标的场景分析报告历史表';
COMMENT ON COLUMN scene_analysis_report.task_id IS '关联的场景分析任务ID';
COMMENT ON COLUMN scene_analysis_report.task_no IS '关联的场景分析任务编号';
COMMENT ON COLUMN scene_analysis_report.generation_type IS '生成类型：initial、regenerate';
COMMENT ON COLUMN scene_analysis_report.version_no IS '同一任务下报告版本号，从1递增';
COMMENT ON COLUMN scene_analysis_report.status IS '报告状态：generating_report、success、failed';
COMMENT ON COLUMN scene_analysis_report.report_content IS 'LLM 输出的结构化报告 JSON';
COMMENT ON COLUMN scene_analysis_report.report_text IS '渲染后的 Markdown 报告文本';

CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_target_generated
    ON scene_analysis_report (target_type, target_code, generated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_task_created
    ON scene_analysis_report (task_no, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_status_updated
    ON scene_analysis_report (status, updated_at DESC);
