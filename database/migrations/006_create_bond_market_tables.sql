CREATE TABLE IF NOT EXISTS bond_config (
    id BIGSERIAL PRIMARY KEY,
    bond_name VARCHAR(100) NOT NULL,
    bond_code VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bond_config_bond_code_exchange UNIQUE (bond_code, exchange_code),
    CONSTRAINT uk_bond_config_secid UNIQUE (secid)
);

COMMENT ON TABLE bond_config IS '可转债查询配置表';
COMMENT ON COLUMN bond_config.bond_name IS '可转债名称';
COMMENT ON COLUMN bond_config.bond_code IS '可转债代码';
COMMENT ON COLUMN bond_config.market_code IS '市场编码';
COMMENT ON COLUMN bond_config.exchange_code IS '交易所编码，SH/SZ';
COMMENT ON COLUMN bond_config.secid IS '外部行情接口证券ID';
COMMENT ON COLUMN bond_config.enabled IS '是否启用查询';

CREATE INDEX IF NOT EXISTS idx_bond_config_market_code ON bond_config (market_code);

CREATE TABLE IF NOT EXISTS bond_quote_snapshot (
    id BIGSERIAL PRIMARY KEY,
    bond_code VARCHAR(32) NOT NULL,
    bond_name VARCHAR(100) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    latest_price NUMERIC(18, 4),
    open_price NUMERIC(18, 4),
    high_price NUMERIC(18, 4),
    low_price NUMERIC(18, 4),
    previous_close_price NUMERIC(18, 4),
    change_amount NUMERIC(18, 4),
    change_percent NUMERIC(10, 4),
    volume BIGINT,
    turnover_amount NUMERIC(24, 4),
    turnover_rate NUMERIC(10, 4),
    amplitude NUMERIC(10, 4),
    conversion_premium_rate NUMERIC(10, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bond_quote_snapshot_secid UNIQUE (secid)
);

COMMENT ON TABLE bond_quote_snapshot IS '可转债最新行情快照表';
COMMENT ON COLUMN bond_quote_snapshot.bond_code IS '可转债代码';
COMMENT ON COLUMN bond_quote_snapshot.bond_name IS '可转债名称';
COMMENT ON COLUMN bond_quote_snapshot.latest_price IS '最新价';
COMMENT ON COLUMN bond_quote_snapshot.change_percent IS '涨跌幅百分比';
COMMENT ON COLUMN bond_quote_snapshot.conversion_premium_rate IS '转股溢价率';

CREATE INDEX IF NOT EXISTS idx_bond_quote_snapshot_market_code ON bond_quote_snapshot (market_code);
CREATE INDEX IF NOT EXISTS idx_bond_quote_snapshot_change_percent ON bond_quote_snapshot (change_percent DESC);

CREATE TABLE IF NOT EXISTS bond_daily_kline (
    id BIGSERIAL PRIMARY KEY,
    bond_code VARCHAR(32) NOT NULL,
    bond_name VARCHAR(100) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    open_price NUMERIC(18, 4),
    close_price NUMERIC(18, 4),
    high_price NUMERIC(18, 4),
    low_price NUMERIC(18, 4),
    change_amount NUMERIC(18, 4),
    change_percent NUMERIC(10, 4),
    volume BIGINT,
    turnover_amount NUMERIC(24, 4),
    amplitude NUMERIC(10, 4),
    turnover_rate NUMERIC(10, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bond_daily_kline_secid_trade_date UNIQUE (secid, trade_date)
);

COMMENT ON TABLE bond_daily_kline IS '可转债日K线表';
COMMENT ON COLUMN bond_daily_kline.bond_code IS '可转债代码';
COMMENT ON COLUMN bond_daily_kline.trade_date IS '交易日期';
COMMENT ON COLUMN bond_daily_kline.close_price IS '收盘价';

CREATE INDEX IF NOT EXISTS idx_bond_daily_kline_date ON bond_daily_kline (bond_code, trade_date DESC);
