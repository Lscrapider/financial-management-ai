-- ================================================================================
-- 01_init_core.sql — 核心表：用户、行情配置、快照、K线、日志
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS / IF EXISTS / ADD COLUMN IF NOT EXISTS）
-- ================================================================================

-- ================================================================================
-- 1. 股票查询配置表
-- ================================================================================
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
COMMENT ON COLUMN stock_config.stock_name IS '股票名称，例如：科前生物';
COMMENT ON COLUMN stock_config.stock_code IS '股票代码，例如：688526';
COMMENT ON COLUMN stock_config.market_code IS '市场编码，例如：ASHARE、STAR、CHINEXT';
COMMENT ON COLUMN stock_config.exchange_code IS '交易所编码，例如：SH、SZ';
COMMENT ON COLUMN stock_config.secid IS '外部行情接口使用的证券 ID，例如：1.688526';
COMMENT ON COLUMN stock_config.enabled IS '是否启用查询';
COMMENT ON COLUMN stock_config.remark IS '备注';
COMMENT ON COLUMN stock_config.created_at IS '创建时间';
COMMENT ON COLUMN stock_config.updated_at IS '更新时间';

-- ================================================================================
-- 2. 股票最新行情快照表（含 021 追加字段，最终精度）
-- ================================================================================
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
COMMENT ON COLUMN stock_quote_snapshot.average_price IS '均价';
COMMENT ON COLUMN stock_quote_snapshot.change_percent IS '涨跌幅百分比';
COMMENT ON COLUMN stock_quote_snapshot.volume IS '成交量';
COMMENT ON COLUMN stock_quote_snapshot.external_volume IS '外盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.internal_volume IS '内盘成交量';
COMMENT ON COLUMN stock_quote_snapshot.current_volume IS '现手';
COMMENT ON COLUMN stock_quote_snapshot.turnover_amount IS '成交额';
COMMENT ON COLUMN stock_quote_snapshot.pe_dynamic IS '动态市盈率';
COMMENT ON COLUMN stock_quote_snapshot.pe_static IS '静态市盈率';
COMMENT ON COLUMN stock_quote_snapshot.raw_response IS '外部接口原始响应';
COMMENT ON COLUMN stock_quote_snapshot.synced_at IS '同步时间';

-- 补列（对已存在旧表的重复执行友好）
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS average_price NUMERIC(18, 3);
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS external_volume BIGINT;
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS internal_volume BIGINT;
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS current_volume BIGINT;
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS pe_dynamic NUMERIC(18, 3);
ALTER TABLE stock_quote_snapshot ADD COLUMN IF NOT EXISTS pe_static NUMERIC(18, 3);

-- ================================================================================
-- 3. 系统登录用户表（含 014 追加字段 + admin 种子数据）
-- ================================================================================
CREATE TABLE IF NOT EXISTS app_user (
                                        id BIGSERIAL PRIMARY KEY,
                                        username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(64) NOT NULL DEFAULT 'admin',
    avatar VARCHAR(500) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    home_path VARCHAR(200) NOT NULL DEFAULT '/analytics',
    introduction TEXT,
    email VARCHAR(128),
    phone VARCHAR(32),
    email_notification BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
    );

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role_code VARCHAR(64) NOT NULL DEFAULT 'admin';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS introduction TEXT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email VARCHAR(128);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email_notification BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON TABLE app_user IS '系统登录用户表';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password IS 'Base64 后的登录密码';
COMMENT ON COLUMN app_user.real_name IS '用户显示名称';
COMMENT ON COLUMN app_user.role_code IS '用户角色编码';
COMMENT ON COLUMN app_user.avatar IS '用户头像地址';
COMMENT ON COLUMN app_user.enabled IS '是否启用';
COMMENT ON COLUMN app_user.home_path IS '登录后默认首页';

-- 默认管理员
INSERT INTO app_user (username, password, real_name, role_code, home_path)
VALUES ('admin', 'MTIzNDU2', 'Admin', 'admin', '/analytics')
    ON CONFLICT (username) DO NOTHING;

-- ================================================================================
-- 4. 指数查询配置表
-- ================================================================================
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
COMMENT ON COLUMN index_config.secid IS '外部行情接口证券 ID，例如：1.000001';
COMMENT ON COLUMN index_config.enabled IS '是否启用查询';

-- 核心指数种子数据
INSERT INTO index_config (index_name, index_code, market_code, exchange_code, secid, remark)
VALUES
    ('上证指数', '000001', 'INDEX', 'SH', '1.000001', '默认指数'),
    ('科创50',   '000688', 'INDEX', 'SH', '1.000688', '默认指数'),
    ('深证成指', '399001', 'INDEX', 'SZ', '0.399001', '默认指数'),
    ('创业板指', '399006', 'INDEX', 'SZ', '0.399006', '默认指数'),
    ('沪深300',  '000300', 'INDEX', 'SH', '1.000300', '默认指数')
    ON CONFLICT (secid) DO NOTHING;

-- ================================================================================
-- 5. 指数最新行情快照表
-- ================================================================================
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
COMMENT ON COLUMN index_quote_snapshot.latest_price IS '最新点位';
COMMENT ON COLUMN index_quote_snapshot.change_percent IS '涨跌幅百分比';

-- ================================================================================
-- 6. 指数K线表（含 031 多周期/均线字段）
-- ================================================================================

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
    period_type VARCHAR(16) NOT NULL DEFAULT 'daily',
    ma5 NUMERIC(20, 4),
    ma10 NUMERIC(20, 4),
    ma20 NUMERIC(20, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

ALTER TABLE index_kline ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';
ALTER TABLE index_kline ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);
ALTER TABLE index_kline ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);
ALTER TABLE index_kline ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

COMMENT ON TABLE index_kline IS '指数K线表';
COMMENT ON COLUMN index_kline.trade_date IS '交易日期';
COMMENT ON COLUMN index_kline.close_price IS '收盘点位';
COMMENT ON COLUMN index_kline.period_type IS '周期类型：daily/weekly/monthly';

-- ================================================================================
-- 7. 可转债查询配置表
-- ================================================================================
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
COMMENT ON COLUMN bond_config.secid IS '外部行情接口证券ID';
COMMENT ON COLUMN bond_config.enabled IS '是否启用查询';

-- ================================================================================
-- 8. 可转债最新行情快照表（含 020/022 追加字段）
-- ================================================================================
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
    bond_rating VARCHAR(16),
    average_price NUMERIC(18, 3),
    current_volume BIGINT,
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bond_quote_snapshot_secid UNIQUE (secid)
    );

ALTER TABLE bond_quote_snapshot ADD COLUMN IF NOT EXISTS bond_rating VARCHAR(16);
ALTER TABLE bond_quote_snapshot ADD COLUMN IF NOT EXISTS average_price NUMERIC(18, 3);
ALTER TABLE bond_quote_snapshot ADD COLUMN IF NOT EXISTS current_volume BIGINT;

COMMENT ON TABLE bond_quote_snapshot IS '可转债最新行情快照表';
COMMENT ON COLUMN bond_quote_snapshot.latest_price IS '最新价';
COMMENT ON COLUMN bond_quote_snapshot.conversion_premium_rate IS '转股溢价率';
COMMENT ON COLUMN bond_quote_snapshot.average_price IS '均价';
COMMENT ON COLUMN bond_quote_snapshot.current_volume IS '现手';

-- ================================================================================
-- 9. 可转债K线表（含 031 多周期/均线字段）
-- ================================================================================

CREATE TABLE IF NOT EXISTS bond_kline (
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
    period_type VARCHAR(16) NOT NULL DEFAULT 'daily',
    ma5 NUMERIC(20, 4),
    ma10 NUMERIC(20, 4),
    ma20 NUMERIC(20, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

ALTER TABLE bond_kline ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';
ALTER TABLE bond_kline ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);
ALTER TABLE bond_kline ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);
ALTER TABLE bond_kline ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

COMMENT ON TABLE bond_kline IS '可转债K线表';
COMMENT ON COLUMN bond_kline.trade_date IS '交易日期';
COMMENT ON COLUMN bond_kline.period_type IS '周期类型：daily/weekly/monthly';

-- ================================================================================
-- 9.1 可转债基础资料表（Tushare 结构化字段）
-- ================================================================================
CREATE TABLE IF NOT EXISTS convertible_bond_basic (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      bond_code VARCHAR(32) NOT NULL,
    bond_name VARCHAR(100) NOT NULL,
    ts_code VARCHAR(32) NOT NULL,
    underlying_stock_code VARCHAR(32),
    underlying_stock_name VARCHAR(100),
    rating VARCHAR(32),
    issue_size NUMERIC(24, 4),
    remaining_size NUMERIC(24, 4),
    first_conversion_price NUMERIC(18, 4),
    conversion_price NUMERIC(18, 4),
    value_date DATE,
    maturity_date DATE,
    maturity_call_price NUMERIC(18, 4),
    coupon_rate TEXT,
    pay_per_year INTEGER,
    conversion_start_date DATE,
    conversion_end_date DATE,
    redeem_clause TEXT,
    putback_clause TEXT,
    reset_clause TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'tushare',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_convertible_bond_basic_bond_code UNIQUE (bond_code)
    );

COMMENT ON TABLE convertible_bond_basic IS '可转债基础资料表';
COMMENT ON COLUMN convertible_bond_basic.remaining_size IS '可转债剩余规模';
COMMENT ON COLUMN convertible_bond_basic.maturity_call_price IS '到期赎回价，用于 YTM 计算';

-- ================================================================================
-- 9.2 可转债每日估值表
-- ================================================================================
CREATE TABLE IF NOT EXISTS convertible_bond_daily_valuation (
                                                                id BIGSERIAL PRIMARY KEY,
                                                                bond_code VARCHAR(32) NOT NULL,
    bond_name VARCHAR(100) NOT NULL,
    ts_code VARCHAR(32),
    trade_date DATE NOT NULL,
    close_price NUMERIC(18, 4),
    conversion_value NUMERIC(18, 4),
    premium_rate NUMERIC(18, 6),
    pure_bond_value NUMERIC(18, 4),
    pure_bond_premium_rate NUMERIC(18, 6),
    ytm NUMERIC(18, 6),
    volume BIGINT,
    turnover_amount NUMERIC(24, 4),
    source VARCHAR(32) NOT NULL DEFAULT 'tushare',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_convertible_bond_daily_valuation_code_date UNIQUE (bond_code, trade_date)
    );

COMMENT ON TABLE convertible_bond_daily_valuation IS '可转债每日估值表';
COMMENT ON COLUMN convertible_bond_daily_valuation.conversion_value IS '转股价值';
COMMENT ON COLUMN convertible_bond_daily_valuation.premium_rate IS '转股溢价率';
COMMENT ON COLUMN convertible_bond_daily_valuation.pure_bond_value IS '纯债价值';

-- ================================================================================
-- 9.3 可转债转股/余额变化表
-- ================================================================================
CREATE TABLE IF NOT EXISTS convertible_bond_share (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      bond_code VARCHAR(32) NOT NULL,
    bond_name VARCHAR(100) NOT NULL,
    ts_code VARCHAR(32),
    end_date DATE NOT NULL,
    issue_size NUMERIC(24, 4),
    conversion_price NUMERIC(18, 4),
    conversion_value NUMERIC(24, 4),
    conversion_volume NUMERIC(24, 4),
    conversion_ratio NUMERIC(18, 6),
    remaining_size NUMERIC(24, 4),
    source VARCHAR(32) NOT NULL DEFAULT 'tushare',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_convertible_bond_share_code_end_date UNIQUE (bond_code, end_date)
    );

COMMENT ON TABLE convertible_bond_share IS '可转债转股/余额变化表';
COMMENT ON COLUMN convertible_bond_share.remaining_size IS '期末可转债剩余规模';

-- ================================================================================
-- 10. 股票K线表（从 stock_daily_kline 迁移，含 030 多周期/复权/均线字段）
-- ================================================================================

CREATE TABLE IF NOT EXISTS stock_kline (
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
    period_type VARCHAR(16) NOT NULL DEFAULT 'daily',
    adjust_type VARCHAR(16) NOT NULL DEFAULT 'hfq',
    ma5 NUMERIC(20, 4),
    ma10 NUMERIC(20, 4),
    ma20 NUMERIC(20, 4),
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

ALTER TABLE stock_kline ADD COLUMN IF NOT EXISTS period_type VARCHAR(16) NOT NULL DEFAULT 'daily';
ALTER TABLE stock_kline ADD COLUMN IF NOT EXISTS adjust_type VARCHAR(16) NOT NULL DEFAULT 'hfq';
ALTER TABLE stock_kline ADD COLUMN IF NOT EXISTS ma5 NUMERIC(20, 4);
ALTER TABLE stock_kline ADD COLUMN IF NOT EXISTS ma10 NUMERIC(20, 4);
ALTER TABLE stock_kline ADD COLUMN IF NOT EXISTS ma20 NUMERIC(20, 4);

COMMENT ON TABLE stock_kline IS '股票K线表（日/周/月，前/后复权）';
COMMENT ON COLUMN stock_kline.trade_date IS '交易日期';
COMMENT ON COLUMN stock_kline.period_type IS '周期类型：daily/weekly/monthly';
COMMENT ON COLUMN stock_kline.adjust_type IS '复权类型：qfq/hfq';

-- ================================================================================
-- 11. AI 模型 Token 用量日志表
-- ================================================================================
CREATE TABLE IF NOT EXISTS ai_token_usage_log (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  provider VARCHAR(32) NOT NULL,
    response_id VARCHAR(128),
    object_type VARCHAR(64),
    model VARCHAR(128),
    finish_reason VARCHAR(64),
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cached_tokens INTEGER NOT NULL DEFAULT 0,
    reasoning_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_hit_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_miss_tokens INTEGER NOT NULL DEFAULT 0,
    raw_response TEXT,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

COMMENT ON TABLE ai_token_usage_log IS 'AI 模型 Token 用量日志表';
COMMENT ON COLUMN ai_token_usage_log.provider IS '模型供应商，例如 deepseek';
COMMENT ON COLUMN ai_token_usage_log.model IS '模型名称';
COMMENT ON COLUMN ai_token_usage_log.prompt_tokens IS '输入 Token 数';
COMMENT ON COLUMN ai_token_usage_log.completion_tokens IS '输出 Token 数';
COMMENT ON COLUMN ai_token_usage_log.total_tokens IS '总 Token 数';

-- ================================================================================
-- 12. 系统访问日志表
-- ================================================================================
CREATE TABLE IF NOT EXISTS app_visit_log (
                                             id BIGSERIAL PRIMARY KEY,
                                             username VARCHAR(64),
    request_method VARCHAR(16) NOT NULL,
    request_uri VARCHAR(500) NOT NULL,
    status_code INTEGER,
    duration_ms BIGINT,
    remote_addr VARCHAR(128),
    user_agent VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

COMMENT ON TABLE app_visit_log IS '系统访问日志表';
COMMENT ON COLUMN app_visit_log.username IS '访问用户';
COMMENT ON COLUMN app_visit_log.request_uri IS '请求路径';
COMMENT ON COLUMN app_visit_log.status_code IS '响应状态码';
COMMENT ON COLUMN app_visit_log.duration_ms IS '请求耗时毫秒';

