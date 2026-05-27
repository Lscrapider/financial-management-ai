CREATE TABLE IF NOT EXISTS stock_alert_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    threshold_percent NUMERIC(10, 4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_active BOOLEAN NOT NULL DEFAULT FALSE,
    last_alert_change_percent NUMERIC(10, 4),
    last_alerted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_alert_config_user_stock UNIQUE (user_id, stock_code)
);

COMMENT ON TABLE stock_alert_config IS '用户股票涨跌幅邮件提醒配置表';
COMMENT ON COLUMN stock_alert_config.user_id IS '用户 ID';
COMMENT ON COLUMN stock_alert_config.stock_code IS '股票代码';
COMMENT ON COLUMN stock_alert_config.stock_name IS '股票名称';
COMMENT ON COLUMN stock_alert_config.threshold_percent IS '涨跌幅阈值百分比';
COMMENT ON COLUMN stock_alert_config.alert_active IS '当前越界提醒是否已触发';
COMMENT ON COLUMN stock_alert_config.last_alerted_at IS '最近一次邮件提醒时间';

CREATE INDEX IF NOT EXISTS idx_stock_alert_config_user_id
    ON stock_alert_config (user_id);

CREATE INDEX IF NOT EXISTS idx_stock_alert_config_enabled
    ON stock_alert_config (enabled);
