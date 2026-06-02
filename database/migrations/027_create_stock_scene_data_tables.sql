CREATE TABLE IF NOT EXISTS stock_industry_info (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    market_code VARCHAR(16),
    exchange_code VARCHAR(16),
    industry_name VARCHAR(100),
    region_name VARCHAR(100),
    concept_names TEXT,
    source VARCHAR(32) NOT NULL DEFAULT 'eastmoney',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_industry_info_secid UNIQUE (secid)
);

COMMENT ON TABLE stock_industry_info IS '股票行业、地域、概念信息';
COMMENT ON COLUMN stock_industry_info.industry_name IS '行业名称';
COMMENT ON COLUMN stock_industry_info.region_name IS '地域板块名称';
COMMENT ON COLUMN stock_industry_info.concept_names IS '概念名称列表，逗号分隔';

CREATE INDEX IF NOT EXISTS idx_stock_industry_info_stock_code
    ON stock_industry_info (stock_code);

CREATE TABLE IF NOT EXISTS stock_valuation_history (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    secid VARCHAR(32) NOT NULL,
    secucode VARCHAR(32) NOT NULL,
    trade_date DATE NOT NULL,
    board_code VARCHAR(32),
    board_name VARCHAR(100),
    total_market_cap NUMERIC(24, 4),
    float_market_cap NUMERIC(24, 4),
    close_price NUMERIC(18, 4),
    change_rate NUMERIC(10, 4),
    total_shares BIGINT,
    free_shares_a BIGINT,
    pe_ttm NUMERIC(18, 6),
    pe_lar NUMERIC(18, 6),
    pb_mrq NUMERIC(18, 6),
    ps_ttm NUMERIC(18, 6),
    peg_car NUMERIC(18, 6),
    source VARCHAR(32) NOT NULL DEFAULT 'eastmoney',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_valuation_history_secid_trade_date UNIQUE (secid, trade_date)
);

COMMENT ON TABLE stock_valuation_history IS '股票每日估值历史';
COMMENT ON COLUMN stock_valuation_history.pe_ttm IS '滚动市盈率';
COMMENT ON COLUMN stock_valuation_history.pb_mrq IS '市净率';

CREATE INDEX IF NOT EXISTS idx_stock_valuation_history_stock_date
    ON stock_valuation_history (stock_code, trade_date DESC);

CREATE INDEX IF NOT EXISTS idx_stock_valuation_history_board_date
    ON stock_valuation_history (board_name, trade_date DESC);

CREATE TABLE IF NOT EXISTS stock_financial_indicator (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    secucode VARCHAR(32) NOT NULL,
    org_type VARCHAR(32),
    report_date DATE NOT NULL,
    report_type VARCHAR(32),
    report_date_name VARCHAR(32),
    notice_date DATE,
    eps_basic NUMERIC(18, 6),
    bps NUMERIC(18, 6),
    total_operate_revenue NUMERIC(24, 4),
    parent_net_profit NUMERIC(24, 4),
    total_operate_revenue_yoy NUMERIC(18, 6),
    parent_net_profit_yoy NUMERIC(18, 6),
    roe_weighted NUMERIC(18, 6),
    debt_asset_ratio NUMERIC(18, 6),
    total_deposits NUMERIC(24, 4),
    gross_loans NUMERIC(24, 4),
    loan_to_deposit_ratio NUMERIC(18, 6),
    capital_adequacy_ratio NUMERIC(18, 6),
    core_tier1_capital_adequacy_ratio NUMERIC(18, 6),
    first_adequacy_ratio NUMERIC(18, 6),
    non_performing_loan_ratio NUMERIC(18, 6),
    provision_coverage_ratio NUMERIC(18, 6),
    net_interest_spread NUMERIC(18, 6),
    net_interest_margin NUMERIC(18, 6),
    loan_provision_ratio NUMERIC(18, 6),
    source VARCHAR(32) NOT NULL DEFAULT 'eastmoney',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_financial_indicator_secucode_report UNIQUE (secucode, report_date, report_type)
);

COMMENT ON TABLE stock_financial_indicator IS '股票财务主指标及银行专项指标';

CREATE INDEX IF NOT EXISTS idx_stock_financial_indicator_stock_report
    ON stock_financial_indicator (stock_code, report_date DESC);

CREATE TABLE IF NOT EXISTS stock_dividend_history (
    id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    secucode VARCHAR(32) NOT NULL,
    report_date DATE,
    ex_dividend_date DATE,
    plan_notice_date DATE,
    equity_record_date DATE,
    pretax_bonus_rmb NUMERIC(18, 6),
    dividend_ratio NUMERIC(18, 6),
    impl_plan_profile VARCHAR(255),
    assign_progress VARCHAR(64),
    basic_eps NUMERIC(18, 6),
    bvps NUMERIC(18, 6),
    parent_net_profit_yoy NUMERIC(18, 6),
    source VARCHAR(32) NOT NULL DEFAULT 'eastmoney',
    raw_response TEXT,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_dividend_history_secucode_report_ex UNIQUE (secucode, report_date, ex_dividend_date)
);

COMMENT ON TABLE stock_dividend_history IS '股票分红股息历史';

CREATE INDEX IF NOT EXISTS idx_stock_dividend_history_stock_ex_date
    ON stock_dividend_history (stock_code, ex_dividend_date DESC);
