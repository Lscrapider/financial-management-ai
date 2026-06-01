ALTER TABLE ocr_task_stage
    ADD COLUMN IF NOT EXISTS chunk_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS chunk_index INTEGER;

ALTER TABLE ocr_task_stage
    DROP CONSTRAINT IF EXISTS uk_ocr_task_stage_task_stage;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_stage
    ON ocr_task_stage (task_no, stage)
    WHERE chunk_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ocr_task_stage_task_chunk_stage
    ON ocr_task_stage (task_no, chunk_id, stage)
    WHERE chunk_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ocr_task_stage_task_chunk
    ON ocr_task_stage (task_no, chunk_index)
    WHERE chunk_id IS NOT NULL;

COMMENT ON COLUMN ocr_task_stage.chunk_id IS 'Chunk 唯一标识；为空表示任务级阶段记录';
COMMENT ON COLUMN ocr_task_stage.chunk_index IS 'Chunk 在任务内的序号；为空表示任务级阶段记录';
