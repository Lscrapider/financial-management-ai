CREATE TABLE IF NOT EXISTS scene_analysis_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    config_profile VARCHAR(64) NOT NULL,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    current_scenes_payload JSONB,
    report_payload JSONB,
    report_text TEXT,
    error_message TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_scene_analysis_task_no UNIQUE (task_no)
);

COMMENT ON TABLE scene_analysis_task IS '标的场景分析任务表';
COMMENT ON COLUMN scene_analysis_task.task_no IS '任务编号';
COMMENT ON COLUMN scene_analysis_task.user_id IS '提交用户ID，来自登录 token';
COMMENT ON COLUMN scene_analysis_task.target_type IS '标的类型：STOCK、INDEX、CONVERTIBLE_BOND';
COMMENT ON COLUMN scene_analysis_task.target_code IS '标的代码';
COMMENT ON COLUMN scene_analysis_task.report_type IS '报告类型';
COMMENT ON COLUMN scene_analysis_task.config_snapshot IS 'Java 合并后的实际参数快照';
COMMENT ON COLUMN scene_analysis_task.status IS '任务状态：pending、processing、success、failed';
COMMENT ON COLUMN scene_analysis_task.current_scenes_payload IS 'Python 计算得到的 currentScenes';
COMMENT ON COLUMN scene_analysis_task.report_payload IS '结构化报告内容';
COMMENT ON COLUMN scene_analysis_task.report_text IS '最终展示报告文本';

CREATE INDEX IF NOT EXISTS idx_scene_analysis_task_user_created
    ON scene_analysis_task (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_scene_analysis_task_status_updated
    ON scene_analysis_task (status, updated_at DESC);
