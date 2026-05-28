CREATE TABLE IF NOT EXISTS watch_group (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_group_user_name UNIQUE (user_id, group_name)
);

COMMENT ON TABLE watch_group IS '投资观察池分组';
COMMENT ON COLUMN watch_group.user_id IS '用户 ID';
COMMENT ON COLUMN watch_group.group_name IS '分组名称';
COMMENT ON COLUMN watch_group.sort_order IS '分组排序';

CREATE INDEX IF NOT EXISTS idx_watch_group_user_sort
    ON watch_group (user_id, sort_order, id);

CREATE TABLE IF NOT EXISTS watch_group_item (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(128) NOT NULL,
    secid VARCHAR(32),
    remark VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_group_item_target UNIQUE (group_id, target_type, target_code),
    CONSTRAINT fk_watch_group_item_group
        FOREIGN KEY (group_id) REFERENCES watch_group (id) ON DELETE CASCADE
);

COMMENT ON TABLE watch_group_item IS '投资观察池分组标的';
COMMENT ON COLUMN watch_group_item.group_id IS '分组 ID';
COMMENT ON COLUMN watch_group_item.user_id IS '用户 ID，冗余用于权限过滤';
COMMENT ON COLUMN watch_group_item.target_type IS '标的类型：STOCK/INDEX/BOND/FUND/SECTOR';
COMMENT ON COLUMN watch_group_item.target_code IS '标的代码';
COMMENT ON COLUMN watch_group_item.target_name IS '标的名称';
COMMENT ON COLUMN watch_group_item.secid IS '行情接口标识';
COMMENT ON COLUMN watch_group_item.remark IS '用户备注';
COMMENT ON COLUMN watch_group_item.sort_order IS '标的排序';

CREATE INDEX IF NOT EXISTS idx_watch_group_item_group_sort
    ON watch_group_item (group_id, target_type, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_watch_group_item_user
    ON watch_group_item (user_id, target_type, target_code);
