CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(64) NOT NULL DEFAULT 'admin',
    avatar VARCHAR(500) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    home_path VARCHAR(200) NOT NULL DEFAULT '/analytics',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS role_code VARCHAR(64) NOT NULL DEFAULT 'admin';

COMMENT ON TABLE app_user IS '系统登录用户表';
COMMENT ON COLUMN app_user.id IS '主键 ID';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password IS 'Base64 后的登录密码';
COMMENT ON COLUMN app_user.real_name IS '用户显示名称';
COMMENT ON COLUMN app_user.role_code IS '用户角色编码';
COMMENT ON COLUMN app_user.avatar IS '用户头像地址';
COMMENT ON COLUMN app_user.enabled IS '是否启用';
COMMENT ON COLUMN app_user.home_path IS '登录后默认首页';
COMMENT ON COLUMN app_user.created_at IS '创建时间';
COMMENT ON COLUMN app_user.updated_at IS '更新时间';

UPDATE app_user
SET role_code = 'admin'
WHERE username = 'admin';

INSERT INTO app_user (username, password, real_name, role_code, home_path)
VALUES ('admin', 'MTIzNDU2', 'Admin', 'admin', '/analytics')
ON CONFLICT (username) DO NOTHING;
