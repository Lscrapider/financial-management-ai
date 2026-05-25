CREATE TABLE IF NOT EXISTS app_visit_log (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64),
    request_method VARCHAR(16) NOT NULL,
    request_uri VARCHAR(500) NOT NULL,
    status_code INTEGER,
    duration_ms BIGINT,
    remote_addr VARCHAR(128),
    user_agent VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app_visit_log IS '系统访问日志表';
COMMENT ON COLUMN app_visit_log.username IS '访问用户';
COMMENT ON COLUMN app_visit_log.request_method IS 'HTTP 方法';
COMMENT ON COLUMN app_visit_log.request_uri IS '请求路径';
COMMENT ON COLUMN app_visit_log.status_code IS '响应状态码';
COMMENT ON COLUMN app_visit_log.duration_ms IS '请求耗时毫秒';
COMMENT ON COLUMN app_visit_log.remote_addr IS '客户端地址';
COMMENT ON COLUMN app_visit_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN app_visit_log.occurred_at IS '访问时间';

CREATE INDEX IF NOT EXISTS idx_app_visit_log_occurred_at
    ON app_visit_log (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_app_visit_log_username
    ON app_visit_log (username);
