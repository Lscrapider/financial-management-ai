-- ================================================================================
-- 05_init_ai_chat_agent.sql — AI Chat 对话短期记忆
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS / ADD COLUMN IF NOT EXISTS）
-- ================================================================================

CREATE TABLE IF NOT EXISTS ai_chat_conversation (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    user_id BIGINT NOT NULL,
                                                    conversation_id VARCHAR(128) NOT NULL,
                                                    title VARCHAR(200),
                                                    status VARCHAR(32) NOT NULL DEFAULT 'active',
                                                    cleanup_version BIGINT NOT NULL DEFAULT 0,
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    cleaned_at TIMESTAMP,
                                                    CONSTRAINT uk_ai_chat_conversation_id UNIQUE (conversation_id)
);

ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS title VARCHAR(200);
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'active';
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS cleanup_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS cleaned_at TIMESTAMP;

COMMENT ON TABLE ai_chat_conversation IS 'AI Chat 对话表';
COMMENT ON COLUMN ai_chat_conversation.user_id IS '对话所属用户 ID';
COMMENT ON COLUMN ai_chat_conversation.conversation_id IS '前端传入并复用的对话 ID';
COMMENT ON COLUMN ai_chat_conversation.status IS '对话状态：active、cleaned';
COMMENT ON COLUMN ai_chat_conversation.cleanup_version IS '延迟清理版本号，用于防止旧 cleanup 消息误删短期记忆';

CREATE TABLE IF NOT EXISTS ai_chat_message (
                                               id BIGSERIAL PRIMARY KEY,
                                               user_id BIGINT NOT NULL,
                                               conversation_id VARCHAR(128) NOT NULL,
                                               message_id VARCHAR(128) NOT NULL,
                                               role VARCHAR(32) NOT NULL,
                                               content TEXT NOT NULL,
                                               metadata_json TEXT,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               CONSTRAINT uk_ai_chat_message_msg UNIQUE (conversation_id, message_id, role)
);

ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS metadata_json TEXT;

COMMENT ON TABLE ai_chat_message IS 'AI Chat 短期消息记忆表';
COMMENT ON COLUMN ai_chat_message.user_id IS '消息所属用户 ID';
COMMENT ON COLUMN ai_chat_message.conversation_id IS '对话 ID';
COMMENT ON COLUMN ai_chat_message.message_id IS '前端消息 ID';
COMMENT ON COLUMN ai_chat_message.role IS '消息角色：user、assistant';
COMMENT ON COLUMN ai_chat_message.content IS '消息内容';

CREATE INDEX IF NOT EXISTS idx_ai_chat_conversation_user_updated
    ON ai_chat_conversation (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_chat_message_user_conversation_created
    ON ai_chat_message (user_id, conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_user_memory (
                                              id BIGSERIAL PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              memory_type VARCHAR(64) NOT NULL,
                                              title VARCHAR(200),
                                              content TEXT NOT NULL,
                                              metadata_json TEXT,
                                              source_conversation_id VARCHAR(128),
                                              confidence NUMERIC(5, 4),
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE ai_user_memory ADD COLUMN IF NOT EXISTS metadata_json TEXT;
ALTER TABLE ai_user_memory ADD COLUMN IF NOT EXISTS source_conversation_id VARCHAR(128);
ALTER TABLE ai_user_memory ADD COLUMN IF NOT EXISTS confidence NUMERIC(5, 4);
ALTER TABLE ai_user_memory ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON TABLE ai_user_memory IS 'AI 用户长期记忆表';
COMMENT ON COLUMN ai_user_memory.memory_type IS '记忆类型：conversation_summary、preference、watch_target、risk_profile';
COMMENT ON COLUMN ai_user_memory.source_conversation_id IS '长期记忆来源对话 ID';

CREATE INDEX IF NOT EXISTS idx_ai_user_memory_user_type_updated
    ON ai_user_memory (user_id, memory_type, updated_at DESC)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_ai_user_memory_source_conversation
    ON ai_user_memory (source_conversation_id);
