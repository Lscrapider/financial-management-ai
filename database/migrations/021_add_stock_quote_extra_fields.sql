ALTER TABLE stock_quote_snapshot
    ADD COLUMN IF NOT EXISTS average_price NUMERIC(18, 3),
    ADD COLUMN IF NOT EXISTS external_volume BIGINT,
    ADD COLUMN IF NOT EXISTS internal_volume BIGINT,
    ADD COLUMN IF NOT EXISTS current_volume BIGINT,
    ADD COLUMN IF NOT EXISTS pe_dynamic NUMERIC(18, 3),
    ADD COLUMN IF NOT EXISTS pe_static NUMERIC(18, 3);

ALTER TABLE stock_quote_snapshot
    ALTER COLUMN latest_price TYPE NUMERIC(18, 3) USING ROUND(latest_price, 3),
    ALTER COLUMN open_price TYPE NUMERIC(18, 3) USING ROUND(open_price, 3),
    ALTER COLUMN high_price TYPE NUMERIC(18, 3) USING ROUND(high_price, 3),
    ALTER COLUMN low_price TYPE NUMERIC(18, 3) USING ROUND(low_price, 3),
    ALTER COLUMN previous_close_price TYPE NUMERIC(18, 3) USING ROUND(previous_close_price, 3),
    ALTER COLUMN change_amount TYPE NUMERIC(18, 3) USING ROUND(change_amount, 3),
    ALTER COLUMN change_percent TYPE NUMERIC(10, 3) USING ROUND(change_percent, 3),
    ALTER COLUMN turnover_amount TYPE NUMERIC(24, 3) USING ROUND(turnover_amount, 3),
    ALTER COLUMN turnover_rate TYPE NUMERIC(10, 3) USING ROUND(turnover_rate, 3),
    ALTER COLUMN amplitude TYPE NUMERIC(10, 3) USING ROUND(amplitude, 3),
    ALTER COLUMN volume_ratio TYPE NUMERIC(10, 3) USING ROUND(volume_ratio, 3),
    ALTER COLUMN limit_up_price TYPE NUMERIC(18, 3) USING ROUND(limit_up_price, 3),
    ALTER COLUMN limit_down_price TYPE NUMERIC(18, 3) USING ROUND(limit_down_price, 3),
    ALTER COLUMN total_market_value TYPE NUMERIC(24, 3) USING ROUND(total_market_value, 3),
    ALTER COLUMN float_market_value TYPE NUMERIC(24, 3) USING ROUND(float_market_value, 3),
    ALTER COLUMN pe_ttm TYPE NUMERIC(18, 3) USING ROUND(pe_ttm, 3),
    ALTER COLUMN pb_ratio TYPE NUMERIC(18, 3) USING ROUND(pb_ratio, 3);

COMMENT ON COLUMN stock_quote_snapshot.average_price IS '均价';
COMMENT ON COLUMN stock_quote_snapshot.external_volume IS '外盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.internal_volume IS '内盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.current_volume IS '现手';
COMMENT ON COLUMN stock_quote_snapshot.pe_dynamic IS '动态市盈率';
COMMENT ON COLUMN stock_quote_snapshot.pe_static IS '静态市盈率';
