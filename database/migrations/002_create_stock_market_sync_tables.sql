CREATE TABLE IF NOT EXISTS stock_quote_snapshot (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    latest_price NUMERIC(18, 3),
    open_price NUMERIC(18, 3),
    high_price NUMERIC(18, 3),
    low_price NUMERIC(18, 3),
    previous_close_price NUMERIC(18, 3),
    average_price NUMERIC(18, 3),
    change_amount NUMERIC(18, 3),
    change_percent NUMERIC(10, 3),
    volume BIGINT,
    external_volume BIGINT,
    internal_volume BIGINT,
    current_volume BIGINT,
    turnover_amount NUMERIC(24, 3),
    turnover_rate NUMERIC(10, 3),
    amplitude NUMERIC(10, 3),
    volume_ratio NUMERIC(10, 3),
    limit_up_price NUMERIC(18, 3),
    limit_down_price NUMERIC(18, 3),
    total_market_value NUMERIC(24, 3),
    float_market_value NUMERIC(24, 3),
    pe_ttm NUMERIC(18, 3),
    pe_dynamic NUMERIC(18, 3),
    pe_static NUMERIC(18, 3),
    pb_ratio NUMERIC(18, 3),
    trade_status INTEGER,
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_quote_snapshot_stock_code UNIQUE (stock_code)
);

COMMENT ON TABLE stock_quote_snapshot IS '股票最新行情快照表';
COMMENT ON COLUMN stock_quote_snapshot.stock_code IS '股票代码';
COMMENT ON COLUMN stock_quote_snapshot.stock_name IS '股票名称';
COMMENT ON COLUMN stock_quote_snapshot.secid IS '外部行情接口证券 ID';
COMMENT ON COLUMN stock_quote_snapshot.latest_price IS '最新价';
COMMENT ON COLUMN stock_quote_snapshot.change_percent IS '涨跌幅百分比';
COMMENT ON COLUMN stock_quote_snapshot.volume IS '成交量';
COMMENT ON COLUMN stock_quote_snapshot.external_volume IS '外盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.internal_volume IS '内盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.current_volume IS '现手';
COMMENT ON COLUMN stock_quote_snapshot.turnover_amount IS '成交额';
COMMENT ON COLUMN stock_quote_snapshot.average_price IS '均价';
COMMENT ON COLUMN stock_quote_snapshot.pe_dynamic IS '动态市盈率';
COMMENT ON COLUMN stock_quote_snapshot.pe_static IS '静态市盈率';
COMMENT ON COLUMN stock_quote_snapshot.raw_response IS '外部接口原始响应';
COMMENT ON COLUMN stock_quote_snapshot.synced_at IS '同步时间';

CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_market_code
    ON stock_quote_snapshot (market_code);

CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_change_percent
    ON stock_quote_snapshot (change_percent DESC);
