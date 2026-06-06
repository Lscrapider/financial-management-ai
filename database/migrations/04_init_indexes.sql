-- ================================================================================
-- 04_init_indexes.sql — 索引与唯一索引
-- 可重复执行，所有索引创建均使用 IF NOT EXISTS
-- ================================================================================

-- ================================================================================
-- 01_init_core
-- ================================================================================
CREATE INDEX IF NOT EXISTS idx_stock_config_stock_name ON stock_config (stock_name);
CREATE INDEX IF NOT EXISTS idx_stock_config_market_code ON stock_config (market_code);
CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_market_code ON stock_quote_snapshot (market_code);
CREATE INDEX IF NOT EXISTS idx_stock_quote_snapshot_change_percent ON stock_quote_snapshot (change_percent DESC);
CREATE INDEX IF NOT EXISTS idx_index_config_market_code ON index_config (market_code);
CREATE INDEX IF NOT EXISTS idx_index_quote_snapshot_market_code ON index_quote_snapshot (market_code);
CREATE INDEX IF NOT EXISTS idx_index_quote_snapshot_change_percent ON index_quote_snapshot (change_percent DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_index_kline_secid_period_date
    ON index_kline (secid, period_type, trade_date);
CREATE INDEX IF NOT EXISTS idx_index_kline_index_period_date
    ON index_kline (index_code, period_type, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_bond_config_market_code ON bond_config (market_code);
CREATE INDEX IF NOT EXISTS idx_bond_quote_snapshot_market_code ON bond_quote_snapshot (market_code);
CREATE INDEX IF NOT EXISTS idx_bond_quote_snapshot_change_percent ON bond_quote_snapshot (change_percent DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bond_kline_secid_period_date
    ON bond_kline (secid, period_type, trade_date);
CREATE INDEX IF NOT EXISTS idx_bond_kline_bond_period_date
    ON bond_kline (bond_code, period_type, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_convertible_bond_basic_underlying
    ON convertible_bond_basic (underlying_stock_code);
CREATE INDEX IF NOT EXISTS idx_convertible_bond_daily_valuation_code_date
    ON convertible_bond_daily_valuation (bond_code, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_convertible_bond_share_code_end_date
    ON convertible_bond_share (bond_code, end_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_kline_secid_period_adjust_date
    ON stock_kline (secid, period_type, adjust_type, trade_date);
CREATE INDEX IF NOT EXISTS idx_stock_kline_stock_period_adjust_date
    ON stock_kline (stock_code, period_type, adjust_type, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_log_occurred_at ON ai_token_usage_log (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_log_provider_model ON ai_token_usage_log (provider, model);
CREATE INDEX IF NOT EXISTS idx_app_visit_log_occurred_at ON app_visit_log (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_visit_log_username ON app_visit_log (username);

-- ================================================================================
-- 02_init_ocr
-- ================================================================================
CREATE INDEX IF NOT EXISTS idx_ocr_task_status_updated_at ON ocr_task (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_submitted_at ON ocr_task (submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_deleted_status_submitted_at ON ocr_task (deleted_at, status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_source_type_submitted_at ON ocr_task (source_type, submitted_at DESC) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_stage
    ON ocr_task_stage (task_no, stage) WHERE chunk_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_chunk_stage
    ON ocr_task_stage (task_no, chunk_id, stage) WHERE chunk_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_no ON ocr_task_stage (task_no);
CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_status_updated_at ON ocr_task_stage (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_chunk ON ocr_task_stage (task_no, chunk_index) WHERE chunk_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ocr_review_status_updated_at ON ocr_review (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_task_no ON knowledge_vector (task_no);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_embedding ON knowledge_vector
    USING hnsw (embedding vector_cosine_ops);

-- ================================================================================
-- 03_init_trading
-- ================================================================================
CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_config_user_type_target
    ON stock_alert_config (user_id, target_type, stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_alert_config_user_id ON stock_alert_config (user_id);
CREATE INDEX IF NOT EXISTS idx_stock_alert_config_enabled ON stock_alert_config (enabled);
CREATE INDEX IF NOT EXISTS idx_watch_group_user_sort ON watch_group (user_id, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_watch_group_item_group_sort ON watch_group_item (group_id, target_type, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_watch_group_item_user ON watch_group_item (user_id, target_type, target_code);
CREATE INDEX IF NOT EXISTS idx_stock_industry_info_stock_code ON stock_industry_info (stock_code);
CREATE INDEX IF NOT EXISTS idx_stock_valuation_history_stock_date ON stock_valuation_history (stock_code, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_stock_valuation_history_board_date ON stock_valuation_history (board_name, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_stock_financial_indicator_stock_report ON stock_financial_indicator (stock_code, report_date DESC);
CREATE INDEX IF NOT EXISTS idx_stock_dividend_history_stock_ex_date ON stock_dividend_history (stock_code, ex_dividend_date DESC);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_task_user_created ON scene_analysis_task (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_task_status_updated ON scene_analysis_task (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_target_generated ON scene_analysis_report (target_type, target_code, generated_at DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_task_created ON scene_analysis_report (task_no, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_report_status_updated ON scene_analysis_report (status, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_scene_analysis_config_profile_system_name
    ON scene_analysis_config_profile (name) WHERE user_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_scene_analysis_config_profile_user_name
    ON scene_analysis_config_profile (user_id, name) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_scene_analysis_config_profile_user_enabled ON scene_analysis_config_profile (user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_scene_analysis_config_profile_group ON scene_analysis_config_profile (config_group);
