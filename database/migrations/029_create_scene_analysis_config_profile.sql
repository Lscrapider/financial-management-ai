CREATE TABLE IF NOT EXISTS scene_analysis_config_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(100) NOT NULL,
    config_group VARCHAR(100) NOT NULL DEFAULT '默认',
    config_profile VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    report_type VARCHAR(64) NOT NULL DEFAULT 'quick_analysis',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    system_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_scene_analysis_config_profile_system_name
    ON scene_analysis_config_profile (name)
    WHERE user_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_scene_analysis_config_profile_user_name
    ON scene_analysis_config_profile (user_id, name)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_scene_analysis_config_profile_user_enabled
    ON scene_analysis_config_profile (user_id, enabled);

CREATE INDEX IF NOT EXISTS idx_scene_analysis_config_profile_group
    ON scene_analysis_config_profile (config_group);

INSERT INTO scene_analysis_config_profile (
    user_id,
    name,
    config_group,
    config_profile,
    target_type,
    report_type,
    config_json,
    system_default,
    enabled
) VALUES
(
    NULL,
    '系统推荐',
    '系统默认',
    'system_recommended',
    NULL,
    'quick_analysis',
    '{"reportType":"quick_analysis","totalChunks":10,"configProfile":"system_recommended","userOverrides":{}}'::jsonb,
    TRUE,
    TRUE
)
ON CONFLICT DO NOTHING;
