-- 股票 K 线表从单日线扩展为日/周/月多周期表。
-- 注意：旧 stock_daily_kline 同步逻辑使用前复权 qfq；迁移时不能把旧数据标记为后复权 hfq。

ALTER TABLE IF EXISTS stock_daily_kline RENAME TO stock_kline;

ALTER TABLE stock_kline
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';

ALTER TABLE stock_kline
    ADD COLUMN IF NOT EXISTS adjust_type VARCHAR(16) NOT NULL DEFAULT 'qfq';

ALTER TABLE stock_kline
    ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);

ALTER TABLE stock_kline
    ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);

ALTER TABLE stock_kline
    ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

ALTER TABLE stock_kline
    ALTER COLUMN adjust_type SET DEFAULT 'hfq';

-- 旧表按 secid + trade_date 建唯一约束；多周期/多复权后需要改成组合唯一键。
ALTER TABLE stock_kline
    DROP CONSTRAINT IF EXISTS uk_stock_daily_kline_secid_trade_date;

ALTER TABLE stock_kline
    DROP CONSTRAINT IF EXISTS stock_daily_kline_secid_trade_date_key;

DROP INDEX IF EXISTS uk_stock_daily_kline_secid_trade_date;

DROP INDEX IF EXISTS idx_stock_daily_kline_secid_trade_date;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_kline_secid_period_adjust_date
    ON stock_kline (secid, period_type, adjust_type, trade_date);

CREATE INDEX IF NOT EXISTS idx_stock_kline_stock_period_adjust_date
    ON stock_kline (stock_code, period_type, adjust_type, trade_date DESC);
