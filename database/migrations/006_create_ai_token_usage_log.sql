CREATE TABLE IF NOT EXISTS ai_token_usage_log (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    response_id VARCHAR(128),
    object_type VARCHAR(64),
    model VARCHAR(128),
    finish_reason VARCHAR(64),
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cached_tokens INTEGER NOT NULL DEFAULT 0,
    reasoning_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_hit_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_miss_tokens INTEGER NOT NULL DEFAULT 0,
    raw_response TEXT,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_token_usage_log IS 'AI 模型 Token 用量日志表';
COMMENT ON COLUMN ai_token_usage_log.provider IS '模型供应商，例如 deepseek';
COMMENT ON COLUMN ai_token_usage_log.response_id IS '模型响应 ID';
COMMENT ON COLUMN ai_token_usage_log.object_type IS '模型响应对象类型';
COMMENT ON COLUMN ai_token_usage_log.model IS '模型名称';
COMMENT ON COLUMN ai_token_usage_log.finish_reason IS '响应结束原因';
COMMENT ON COLUMN ai_token_usage_log.prompt_tokens IS '输入 Token 数';
COMMENT ON COLUMN ai_token_usage_log.completion_tokens IS '输出 Token 数';
COMMENT ON COLUMN ai_token_usage_log.total_tokens IS '总 Token 数';
COMMENT ON COLUMN ai_token_usage_log.cached_tokens IS '命中缓存 Token 数';
COMMENT ON COLUMN ai_token_usage_log.reasoning_tokens IS '推理 Token 数';
COMMENT ON COLUMN ai_token_usage_log.prompt_cache_hit_tokens IS '提示词缓存命中 Token 数';
COMMENT ON COLUMN ai_token_usage_log.prompt_cache_miss_tokens IS '提示词缓存未命中 Token 数';
COMMENT ON COLUMN ai_token_usage_log.raw_response IS '模型原始响应 JSON';
COMMENT ON COLUMN ai_token_usage_log.occurred_at IS '模型响应创建时间';

CREATE INDEX IF NOT EXISTS idx_ai_token_usage_log_occurred_at
    ON ai_token_usage_log (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_token_usage_log_provider_model
    ON ai_token_usage_log (provider, model);
