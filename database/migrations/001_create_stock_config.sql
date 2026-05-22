CREATE TABLE IF NOT EXISTS stock_config (
    id BIGSERIAL PRIMARY KEY,
    stock_name VARCHAR(100) NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    market_code VARCHAR(16) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_config_stock_code UNIQUE (stock_code),
    CONSTRAINT uk_stock_config_secid UNIQUE (secid)
);

COMMENT ON TABLE stock_config IS '股票查询配置表';
COMMENT ON COLUMN stock_config.id IS '主键 ID';
COMMENT ON COLUMN stock_config.stock_name IS '股票名称，例如：科前生物';
COMMENT ON COLUMN stock_config.stock_code IS '股票代码，例如：688526';
COMMENT ON COLUMN stock_config.market_code IS '市场编码，例如：ASHARE、STAR、CHINEXT';
COMMENT ON COLUMN stock_config.exchange_code IS '交易所编码，例如：SH、SZ';
COMMENT ON COLUMN stock_config.secid IS '外部行情接口使用的证券 ID，例如：1.688526';
COMMENT ON COLUMN stock_config.enabled IS '是否启用查询';
COMMENT ON COLUMN stock_config.remark IS '备注';
COMMENT ON COLUMN stock_config.created_at IS '创建时间';
COMMENT ON COLUMN stock_config.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_stock_config_stock_name
    ON stock_config (stock_name);

CREATE INDEX IF NOT EXISTS idx_stock_config_market_code
    ON stock_config (market_code);
