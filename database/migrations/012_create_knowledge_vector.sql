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

CREATE INDEX IF NOT EXISTS idx_knowledge_vector_task_no ON knowledge_vector(task_no);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_embedding ON knowledge_vector
    USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE knowledge_vector IS '知识库向量存储表';
COMMENT ON COLUMN knowledge_vector.task_no IS '关联的 OCR 任务编号';
COMMENT ON COLUMN knowledge_vector.chunk_index IS '同一任务内的分段序号';
COMMENT ON COLUMN knowledge_vector.text IS '分段文本内容';
COMMENT ON COLUMN knowledge_vector.embedding IS '文本向量，维度 512';
COMMENT ON COLUMN knowledge_vector.metadata IS '包含 taskNo/documentId/chunkId/pageNos/paragraphNos/sourceType/version/deleted 等元数据';
