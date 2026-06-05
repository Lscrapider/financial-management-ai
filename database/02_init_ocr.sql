-- ================================================================================
-- 02_init_ocr.sql — OCR 管道：任务、阶段、复核、知识库向量
-- 可重复执行，所有操作均为幂等（IF NOT EXISTS / IF EXISTS / ADD COLUMN IF NOT EXISTS）
-- ================================================================================

-- ================================================================================
-- 1. OCR 识别任务表（含 011 软删除 + 024 来源类型）
-- ================================================================================
CREATE TABLE IF NOT EXISTS ocr_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ready',
    current_stage VARCHAR(64) NOT NULL DEFAULT 'document.normalize',
    progress INTEGER NOT NULL DEFAULT 0,
    page_count INTEGER NOT NULL DEFAULT 0,
    segment_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    source_type VARCHAR(32) NOT NULL DEFAULT 'ocr',
    deleted_at TIMESTAMP,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ocr_task_task_no UNIQUE (task_no)
);

ALTER TABLE ocr_task ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE ocr_task ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'ocr';

COMMENT ON TABLE ocr_task IS 'OCR 识别任务表';
COMMENT ON COLUMN ocr_task.task_no IS '任务编号';
COMMENT ON COLUMN ocr_task.original_filename IS '原始文件名';
COMMENT ON COLUMN ocr_task.stored_filename IS '存储文件名';
COMMENT ON COLUMN ocr_task.file_type IS '文件类型，例如 pdf、png、jpg';
COMMENT ON COLUMN ocr_task.status IS '任务状态：ready、running、manual_review_required、finished、failed';
COMMENT ON COLUMN ocr_task.current_stage IS '当前处理阶段：document.normalize、ocr.recognize、text.clean、quality.validate、chunk.tag.rule、chunk.tag.llm、chunk.tag.correct、embedding.index';
COMMENT ON COLUMN ocr_task.progress IS '处理进度百分比';
COMMENT ON COLUMN ocr_task.page_count IS '扫描页数';
COMMENT ON COLUMN ocr_task.segment_count IS '文本分段数';
COMMENT ON COLUMN ocr_task.error_message IS '失败原因';
COMMENT ON COLUMN ocr_task.source_type IS '任务来源类型：ocr、manual_text';
COMMENT ON COLUMN ocr_task.deleted_at IS '软删除时间';
COMMENT ON COLUMN ocr_task.submitted_at IS '提交时间';

CREATE INDEX IF NOT EXISTS idx_ocr_task_status_updated_at ON ocr_task (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_submitted_at ON ocr_task (submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_deleted_status_submitted_at ON ocr_task (deleted_at, status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_source_type_submitted_at ON ocr_task (source_type, submitted_at DESC) WHERE deleted_at IS NULL;

-- ================================================================================
-- 2. OCR 任务阶段处理记录表（含 023 chunk 级阶段记录）
-- ================================================================================
CREATE TABLE IF NOT EXISTS ocr_task_stage (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    input_message JSONB,
    output_message JSONB,
    input_ref JSONB,
    output_ref JSONB,
    metrics JSONB,
    error_message TEXT,
    chunk_id VARCHAR(128),
    chunk_index INTEGER,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ocr_task_stage ADD COLUMN IF NOT EXISTS chunk_id VARCHAR(128);
ALTER TABLE ocr_task_stage ADD COLUMN IF NOT EXISTS chunk_index INTEGER;

COMMENT ON TABLE ocr_task_stage IS 'OCR 任务阶段处理记录表';
COMMENT ON COLUMN ocr_task_stage.task_no IS '任务编号';
COMMENT ON COLUMN ocr_task_stage.stage IS '处理阶段';
COMMENT ON COLUMN ocr_task_stage.status IS '阶段状态：pending、running、finished、failed';
COMMENT ON COLUMN ocr_task_stage.input_message IS '阶段消费到的 RabbitMQ 消息体';
COMMENT ON COLUMN ocr_task_stage.output_message IS '阶段发布到下一阶段的 RabbitMQ 消息体';
COMMENT ON COLUMN ocr_task_stage.input_ref IS '阶段输入产物引用';
COMMENT ON COLUMN ocr_task_stage.output_ref IS '阶段输出产物引用';
COMMENT ON COLUMN ocr_task_stage.metrics IS '阶段处理指标';
COMMENT ON COLUMN ocr_task_stage.error_message IS '阶段失败原因';
COMMENT ON COLUMN ocr_task_stage.chunk_id IS 'Chunk 唯一标识；为空表示任务级阶段记录';
COMMENT ON COLUMN ocr_task_stage.chunk_index IS 'Chunk 在任务内的序号；为空表示任务级阶段记录';

-- 清理旧约束
ALTER TABLE ocr_task_stage DROP CONSTRAINT IF EXISTS uk_ocr_task_stage_task_stage;

-- 任务级阶段唯一（chunk_id IS NULL）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_stage
    ON ocr_task_stage (task_no, stage) WHERE chunk_id IS NULL;

-- Chunk 级阶段唯一（chunk_id IS NOT NULL）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_chunk_stage
    ON ocr_task_stage (task_no, chunk_id, stage) WHERE chunk_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_no ON ocr_task_stage (task_no);
CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_status_updated_at ON ocr_task_stage (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_chunk ON ocr_task_stage (task_no, chunk_index) WHERE chunk_id IS NOT NULL;

-- ================================================================================
-- 3. OCR 人工复核任务表
-- ================================================================================
CREATE TABLE IF NOT EXISTS ocr_review (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    cleaned_ref JSONB NOT NULL,
    reviewed_ref JSONB,
    draft_content JSONB,
    overall_confidence NUMERIC(6, 4),
    paragraph_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    reviewer_id BIGINT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ocr_review IS 'OCR 人工复核任务表';
COMMENT ON COLUMN ocr_review.task_no IS '任务编号';
COMMENT ON COLUMN ocr_review.status IS '复核状态：pending、saved、approved、rejected';
COMMENT ON COLUMN ocr_review.cleaned_ref IS '文本清洗阶段 cleaned.json 对象存储引用';
COMMENT ON COLUMN ocr_review.reviewed_ref IS '人工确认后 reviewed.json 对象存储引用';
COMMENT ON COLUMN ocr_review.draft_content IS '人工复核草稿内容';
COMMENT ON COLUMN ocr_review.overall_confidence IS '整体置信度';
COMMENT ON COLUMN ocr_review.paragraph_count IS '段落数量';
COMMENT ON COLUMN ocr_review.warning_count IS '警告数量';
COMMENT ON COLUMN ocr_review.reviewer_id IS '复核人 ID';
COMMENT ON COLUMN ocr_review.reviewed_at IS '确认复核时间';

CREATE INDEX IF NOT EXISTS idx_ocr_review_status_updated_at ON ocr_review (status, updated_at DESC);

-- ================================================================================
-- 4. 知识库向量存储表（pgvector）
-- ================================================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_vector (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    embedding vector(512),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE knowledge_vector IS '知识库向量存储表';
COMMENT ON COLUMN knowledge_vector.task_no IS '关联的 OCR 任务编号';
COMMENT ON COLUMN knowledge_vector.chunk_index IS '同一任务内的分段序号';
COMMENT ON COLUMN knowledge_vector.text IS '分段文本内容';
COMMENT ON COLUMN knowledge_vector.embedding IS '文本向量，维度 512';
COMMENT ON COLUMN knowledge_vector.metadata IS '包含 taskNo/documentId/chunkId/pageNos/paragraphNos/sourceType/scenes/keywords/summary/tagging/version/deleted 等元数据';

CREATE INDEX IF NOT EXISTS idx_knowledge_vector_task_no ON knowledge_vector (task_no);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_embedding ON knowledge_vector
    USING hnsw (embedding vector_cosine_ops);
