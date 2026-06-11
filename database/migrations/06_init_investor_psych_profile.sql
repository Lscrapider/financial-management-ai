-- ================================================================================
-- 06_init_investor_psych_profile.sql — 投资心理画像
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS）
-- ================================================================================

CREATE TABLE IF NOT EXISTS ai_investor_psych_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    risk_emotion VARCHAR(64) NOT NULL,
    decision_style VARCHAR(64) NOT NULL,
    holding_mindset_json TEXT NOT NULL,
    trading_tempo VARCHAR(64) NOT NULL,
    explanation_preference VARCHAR(64) NOT NULL,
    advice_style VARCHAR(64) NOT NULL,
    raw_advice_style VARCHAR(64),
    tag_scores_json TEXT NOT NULL,
    questionnaire_answers_json TEXT NOT NULL,
    summary TEXT,
    confirmed_by_user BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_investor_psych_profile_user_active
    ON ai_investor_psych_profile (user_id)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS idx_ai_investor_psych_profile_user_version
    ON ai_investor_psych_profile (user_id, profile_version DESC);

COMMENT ON TABLE ai_investor_psych_profile IS 'AI Chat 投资心理画像表';
COMMENT ON COLUMN ai_investor_psych_profile.advice_style IS '系统推导的建议强度，不允许用户直接选择';
COMMENT ON COLUMN ai_investor_psych_profile.raw_advice_style IS '未做产品降级前的原始建议强度';
COMMENT ON COLUMN ai_investor_psych_profile.questionnaire_answers_json IS '10题问卷答案 JSON';
