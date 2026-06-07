-- ================================================================================
-- 03_init_trading.sql — 交易辅助：预警、观察池、场景分析、报告、基本面数据
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS / IF EXISTS / ADD COLUMN IF NOT EXISTS）
-- ================================================================================

-- ================================================================================
-- 1. 股票涨跌幅预警配置表（含 017 target_type，不含 015 已删除的 notify_email）
-- ================================================================================
CREATE TABLE IF NOT EXISTS stock_alert_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(32) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    target_type VARCHAR(16) NOT NULL DEFAULT 'STOCK',
    threshold_percent NUMERIC(10, 4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_active BOOLEAN NOT NULL DEFAULT FALSE,
    last_alert_change_percent NUMERIC(10, 4),
    last_alerted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE stock_alert_config ADD COLUMN IF NOT EXISTS target_type VARCHAR(16) NOT NULL DEFAULT 'STOCK';

COMMENT ON TABLE stock_alert_config IS '用户股票涨跌幅提醒配置表';
COMMENT ON COLUMN stock_alert_config.user_id IS '用户 ID';
COMMENT ON COLUMN stock_alert_config.stock_code IS '标的代码';
COMMENT ON COLUMN stock_alert_config.stock_name IS '标的名称';
COMMENT ON COLUMN stock_alert_config.target_type IS '目标类型：STOCK/INDEX/BOND';
COMMENT ON COLUMN stock_alert_config.threshold_percent IS '涨跌幅阈值百分比';
COMMENT ON COLUMN stock_alert_config.alert_active IS '当前越界提醒是否已触发';

-- ================================================================================
-- 2. 投资观察池
-- ================================================================================
CREATE TABLE IF NOT EXISTS watch_group (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_group_user_name UNIQUE (user_id, group_name)
);

COMMENT ON TABLE watch_group IS '投资观察池分组';
COMMENT ON COLUMN watch_group.group_name IS '分组名称';
COMMENT ON COLUMN watch_group.sort_order IS '分组排序';

-- 含 021 追加字段 buy_price/position
CREATE TABLE IF NOT EXISTS watch_group_item (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(128) NOT NULL,
    secid VARCHAR(32),
    remark VARCHAR(255),
    buy_price DECIMAL(18, 4),
    position DECIMAL(18, 4),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_watch_group_item_target UNIQUE (group_id, target_type, target_code),
    CONSTRAINT fk_watch_group_item_group
        FOREIGN KEY (group_id) REFERENCES watch_group (id) ON DELETE CASCADE
);

ALTER TABLE watch_group_item ADD COLUMN IF NOT EXISTS buy_price DECIMAL(18, 4);
ALTER TABLE watch_group_item ADD COLUMN IF NOT EXISTS position DECIMAL(18, 4);

COMMENT ON TABLE watch_group_item IS '投资观察池分组标的';
COMMENT ON COLUMN watch_group_item.group_id IS '分组 ID';
COMMENT ON COLUMN watch_group_item.user_id IS '用户 ID，冗余用于权限过滤';
COMMENT ON COLUMN watch_group_item.target_type IS '标的类型：STOCK/INDEX/BOND/FUND/SECTOR';
COMMENT ON COLUMN watch_group_item.target_code IS '标的代码';
COMMENT ON COLUMN watch_group_item.target_name IS '标的名称';
COMMENT ON COLUMN watch_group_item.buy_price IS '买入价格（用户建仓成本）';
COMMENT ON COLUMN watch_group_item.position IS '持仓数量（股/张/份）';
COMMENT ON COLUMN watch_group_item.remark IS '用户备注';

-- ================================================================================
-- 3. 行情同步任务记录
-- ================================================================================
CREATE TABLE IF NOT EXISTS market_sync_job (
    id BIGSERIAL PRIMARY KEY,
    job_no VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    sync_mode VARCHAR(32) NOT NULL,
    data_scope VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_sync_job_no UNIQUE (job_no)
);

COMMENT ON TABLE market_sync_job IS '行情同步任务记录';
COMMENT ON COLUMN market_sync_job.job_no IS '同步任务编号';
COMMENT ON COLUMN market_sync_job.target_type IS '标的类型：stock/index/bond';
COMMENT ON COLUMN market_sync_job.sync_mode IS '同步模式：full/single';
COMMENT ON COLUMN market_sync_job.data_scope IS '数据范围：all/quote/trend/kline';
COMMENT ON COLUMN market_sync_job.trigger_type IS '触发方式：manual/scheduled';
COMMENT ON COLUMN market_sync_job.target_code IS '单标的同步代码，全量同步为空';
COMMENT ON COLUMN market_sync_job.status IS '任务状态：running/success/failed';

-- ================================================================================
-- 4. 股票行业/地域/概念信息
-- ================================================================================
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

-- ================================================================================
-- 5. 股票每日估值历史
-- ================================================================================
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

-- ================================================================================
-- 6. 股票财务主指标及银行专项指标
-- ================================================================================
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

-- ================================================================================
-- 7. 股票分红股息历史
-- ================================================================================
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

-- ================================================================================
-- 8. 标的场景分析任务表
-- ================================================================================
CREATE TABLE IF NOT EXISTS scene_analysis_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    config_profile VARCHAR(64) NOT NULL,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    current_scenes_payload JSONB,
    report_payload JSONB,
    report_text TEXT,
    error_message TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_scene_analysis_task_no UNIQUE (task_no)
);

COMMENT ON TABLE scene_analysis_task IS '标的场景分析任务表';
COMMENT ON COLUMN scene_analysis_task.task_no IS '任务编号';
COMMENT ON COLUMN scene_analysis_task.user_id IS '提交用户ID';
COMMENT ON COLUMN scene_analysis_task.target_type IS '标的类型：STOCK、INDEX、CONVERTIBLE_BOND';
COMMENT ON COLUMN scene_analysis_task.target_code IS '标的代码';
COMMENT ON COLUMN scene_analysis_task.report_type IS '报告类型';
COMMENT ON COLUMN scene_analysis_task.config_snapshot IS 'Java 合并后的实际参数快照';
COMMENT ON COLUMN scene_analysis_task.status IS '任务状态：pending、processing、success、failed';
COMMENT ON COLUMN scene_analysis_task.current_scenes_payload IS 'Python 计算得到的 currentScenes';
COMMENT ON COLUMN scene_analysis_task.report_payload IS '结构化报告内容';
COMMENT ON COLUMN scene_analysis_task.report_text IS '最终展示报告文本';

-- ================================================================================
-- 9. 标的场景分析报告历史表
-- ================================================================================
CREATE TABLE IF NOT EXISTS scene_analysis_report (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(32) NOT NULL,
    target_name VARCHAR(100),
    report_type VARCHAR(64) NOT NULL,
    generation_type VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'generating_report',
    report_content JSONB,
    report_text TEXT,
    model VARCHAR(100),
    error_message TEXT,
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scene_analysis_report_task
        FOREIGN KEY (task_id) REFERENCES scene_analysis_task (id),
    CONSTRAINT uk_scene_analysis_report_task_version
        UNIQUE (task_id, version_no)
);

COMMENT ON TABLE scene_analysis_report IS '标的场景分析报告历史表';
COMMENT ON COLUMN scene_analysis_report.task_id IS '关联的场景分析任务ID';
COMMENT ON COLUMN scene_analysis_report.generation_type IS '生成类型：initial、regenerate';
COMMENT ON COLUMN scene_analysis_report.version_no IS '同一任务下报告版本号，从1递增';
COMMENT ON COLUMN scene_analysis_report.status IS '报告状态：generating_report、success、failed';
COMMENT ON COLUMN scene_analysis_report.report_content IS 'LLM 输出的结构化报告 JSON';
COMMENT ON COLUMN scene_analysis_report.report_text IS '渲染后的 Markdown 报告文本';

-- ================================================================================
-- 9. 场景分析配置模板
-- ================================================================================
CREATE TABLE IF NOT EXISTS scene_analysis_config_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    name VARCHAR(100) NOT NULL,
    config_group VARCHAR(100) NOT NULL DEFAULT '默认',
    config_profile VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    report_type VARCHAR(64) NOT NULL DEFAULT 'quick_analysis',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    system_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 系统默认配置
INSERT INTO scene_analysis_config_profile (
    user_id, name, config_group, config_profile, target_type, report_type, config_json, system_default, enabled
) VALUES (
    NULL,
    '系统推荐',
    '系统默认',
    'system_recommended',
    NULL,
    'quick_analysis',
    '{"reportType":"quick_analysis","totalChunks":10,"configProfile":"system_recommended","userOverrides":{}}'::jsonb,
    TRUE,
    TRUE
)
ON CONFLICT DO NOTHING;
