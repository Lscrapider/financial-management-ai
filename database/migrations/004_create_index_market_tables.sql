CREATE TABLE IF NOT EXISTS index_config (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    index_code VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_index_config_index_code_exchange UNIQUE (index_code, exchange_code),
    CONSTRAINT uk_index_config_secid UNIQUE (secid)
);

COMMENT ON TABLE index_config IS '指数查询配置表';
COMMENT ON COLUMN index_config.index_name IS '指数名称，例如：上证指数';
COMMENT ON COLUMN index_config.index_code IS '指数代码，例如：000001';
COMMENT ON COLUMN index_config.market_code IS '市场编码，例如：INDEX';
COMMENT ON COLUMN index_config.exchange_code IS '交易所编码，例如：SH、SZ';
COMMENT ON COLUMN index_config.secid IS '外部行情接口证券 ID，例如：1.000001';
COMMENT ON COLUMN index_config.enabled IS '是否启用查询';

CREATE INDEX IF NOT EXISTS idx_index_config_market_code
    ON index_config (market_code);

CREATE TABLE IF NOT EXISTS index_quote_snapshot (
    id BIGSERIAL PRIMARY KEY,
    index_code VARCHAR(32) NOT NULL,
    index_name VARCHAR(100) NOT NULL,
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
    amplitude NUMERIC(10, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_index_quote_snapshot_secid UNIQUE (secid)
);

COMMENT ON TABLE index_quote_snapshot IS '指数最新行情快照表';
COMMENT ON COLUMN index_quote_snapshot.index_code IS '指数代码';
COMMENT ON COLUMN index_quote_snapshot.index_name IS '指数名称';
COMMENT ON COLUMN index_quote_snapshot.secid IS '外部行情接口证券 ID';
COMMENT ON COLUMN index_quote_snapshot.latest_price IS '最新点位';
COMMENT ON COLUMN index_quote_snapshot.change_percent IS '涨跌幅百分比';
COMMENT ON COLUMN index_quote_snapshot.volume IS '成交量';
COMMENT ON COLUMN index_quote_snapshot.turnover_amount IS '成交额';
COMMENT ON COLUMN index_quote_snapshot.raw_response IS '外部接口原始响应';
COMMENT ON COLUMN index_quote_snapshot.synced_at IS '同步时间';

CREATE INDEX IF NOT EXISTS idx_index_quote_snapshot_market_code
    ON index_quote_snapshot (market_code);

CREATE INDEX IF NOT EXISTS idx_index_quote_snapshot_change_percent
    ON index_quote_snapshot (change_percent DESC);

CREATE TABLE IF NOT EXISTS index_kline (
    id BIGSERIAL PRIMARY KEY,
    index_code VARCHAR(32) NOT NULL,
    index_name VARCHAR(100) NOT NULL,
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
    CONSTRAINT uk_index_kline_secid_trade_date UNIQUE (secid, trade_date)
);

COMMENT ON TABLE index_kline IS '指数K线表';
COMMENT ON COLUMN index_kline.index_code IS '指数代码';
COMMENT ON COLUMN index_kline.index_name IS '指数名称';
COMMENT ON COLUMN index_kline.trade_date IS '交易日期';
COMMENT ON COLUMN index_kline.close_price IS '收盘点位';
COMMENT ON COLUMN index_kline.change_percent IS '涨跌幅百分比';

CREATE INDEX IF NOT EXISTS idx_index_kline_index_date
    ON index_kline (index_code, trade_date DESC);
