CREATE TABLE IF NOT EXISTS stock_quote_snapshot (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
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
    volume_ratio NUMERIC(10, 4),
    limit_up_price NUMERIC(18, 4),
    limit_down_price NUMERIC(18, 4),
    total_market_value NUMERIC(24, 4),
    float_market_value NUMERIC(24, 4),
    pe_ttm NUMERIC(18, 4),
    pb_ratio NUMERIC(18, 4),
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
COMMENT ON COLUMN stock_quote_snapshot.turnover_amount IS '成交额';
COMMENT ON COLUMN stock_quote_snapshot.raw_response IS '外部接口原始响应';
COMMENT ON COLUMN stock_quote_snapshot.synced_at IS '同步时间';

CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_market_code
    ON stock_quote_snapshot (market_code);

CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_change_percent
    ON stock_quote_snapshot (change_percent DESC);
