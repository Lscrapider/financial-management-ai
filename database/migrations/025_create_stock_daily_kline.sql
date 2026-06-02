CREATE TABLE IF NOT EXISTS stock_daily_kline (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
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
    CONSTRAINT uk_stock_daily_kline_secid_trade_date UNIQUE (secid, trade_date)
);

COMMENT ON TABLE stock_daily_kline IS '股票日K线表';
COMMENT ON COLUMN stock_daily_kline.stock_code IS '股票代码';
COMMENT ON COLUMN stock_daily_kline.stock_name IS '股票名称';
COMMENT ON COLUMN stock_daily_kline.trade_date IS '交易日期';
COMMENT ON COLUMN stock_daily_kline.close_price IS '收盘价';
COMMENT ON COLUMN stock_daily_kline.change_percent IS '涨跌幅百分比';

CREATE INDEX IF NOT EXISTS idx_stock_daily_kline_stock_date
    ON stock_daily_kline (stock_code, trade_date DESC);
