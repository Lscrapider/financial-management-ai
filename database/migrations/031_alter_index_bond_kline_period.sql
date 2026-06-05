-- 指数/可转债 K 线表从单日线扩展为日/周/月多周期表。
-- 指数和可转债不使用复权类型，按 secid + period_type + trade_date 唯一。

ALTER TABLE IF EXISTS index_daily_kline RENAME TO index_kline;

ALTER TABLE index_kline
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';

ALTER TABLE index_kline
    ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);

ALTER TABLE index_kline
    ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);

ALTER TABLE index_kline
    ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

ALTER TABLE index_kline
    DROP CONSTRAINT IF EXISTS uk_index_daily_kline_secid_trade_date;

ALTER TABLE index_kline
    DROP CONSTRAINT IF EXISTS index_daily_kline_secid_trade_date_key;

ALTER TABLE index_kline
    DROP CONSTRAINT IF EXISTS uk_index_kline_secid_trade_date;

ALTER TABLE index_kline
    DROP CONSTRAINT IF EXISTS index_kline_secid_trade_date_key;

DROP INDEX IF EXISTS uk_index_kline_secid_trade_date;

DROP INDEX IF EXISTS idx_index_daily_kline_index_date;

DROP INDEX IF EXISTS idx_index_kline_index_date;

CREATE UNIQUE INDEX IF NOT EXISTS uk_index_kline_secid_period_date
    ON index_kline (secid, period_type, trade_date);

CREATE INDEX IF NOT EXISTS idx_index_kline_index_period_date
    ON index_kline (index_code, period_type, trade_date DESC);

ALTER TABLE IF EXISTS bond_daily_kline RENAME TO bond_kline;

ALTER TABLE bond_kline
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';

ALTER TABLE bond_kline
    ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);

ALTER TABLE bond_kline
    ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);

ALTER TABLE bond_kline
    ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

ALTER TABLE bond_kline
    DROP CONSTRAINT IF EXISTS uk_bond_daily_kline_secid_trade_date;

ALTER TABLE bond_kline
    DROP CONSTRAINT IF EXISTS bond_daily_kline_secid_trade_date_key;

ALTER TABLE bond_kline
    DROP CONSTRAINT IF EXISTS uk_bond_kline_secid_trade_date;

ALTER TABLE bond_kline
    DROP CONSTRAINT IF EXISTS bond_kline_secid_trade_date_key;

DROP INDEX IF EXISTS uk_bond_kline_secid_trade_date;

DROP INDEX IF EXISTS idx_bond_daily_kline_date;

DROP INDEX IF EXISTS idx_bond_kline_date;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bond_kline_secid_period_date
    ON bond_kline (secid, period_type, trade_date);

CREATE INDEX IF NOT EXISTS idx_bond_kline_bond_period_date
    ON bond_kline (bond_code, period_type, trade_date DESC);
