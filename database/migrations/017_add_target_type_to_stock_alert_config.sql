ALTER TABLE stock_alert_config
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(16) NOT NULL DEFAULT 'STOCK';

COMMENT ON COLUMN stock_alert_config.target_type IS '目标类型：STOCK/INDEX/BOND';

ALTER TABLE stock_alert_config
    DROP CONSTRAINT IF EXISTS uk_stock_alert_config_user_stock;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_alert_config_user_type_target'
    ) THEN
        ALTER TABLE stock_alert_config
            ADD CONSTRAINT uk_alert_config_user_type_target UNIQUE (user_id, target_type, stock_code);
    END IF;
END $$;
