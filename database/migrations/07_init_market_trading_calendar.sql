-- ================================================================================
-- 07_init_market_trading_calendar.sql — 行情交易日历缓存
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS）
-- ================================================================================

CREATE TABLE IF NOT EXISTS market_trading_calendar (
    id BIGSERIAL PRIMARY KEY,
    exchange VARCHAR(16) NOT NULL,
    calendar_date DATE NOT NULL,
    is_open BOOLEAN NOT NULL,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_trading_calendar_exchange_date UNIQUE (exchange, calendar_date)
);

COMMENT ON TABLE market_trading_calendar IS '行情交易日历缓存表';
COMMENT ON COLUMN market_trading_calendar.exchange IS '交易所代码，例如 SSE';
COMMENT ON COLUMN market_trading_calendar.calendar_date IS '自然日';
COMMENT ON COLUMN market_trading_calendar.is_open IS '是否开市';
COMMENT ON COLUMN market_trading_calendar.synced_at IS '从行情源同步时间';
