package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.OcrTaskStagePO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OcrTaskStageMapper extends BaseMapper<OcrTaskStagePO> {

    @Insert("""
            INSERT INTO ocr_task_stage (
                task_no, stage, status, attempt_count, max_attempts,
                input_message, input_ref, error_message,
                started_at, finished_at, updated_at, created_at
            )
            VALUES (
                #{taskNo}, #{stage}, 'running', #{attemptCount}, #{maxAttempts},
                CAST(#{inputMessageJson} AS jsonb), CAST(#{inputRefJson} AS jsonb), NULL,
                #{startedAt}, NULL, #{updatedAt}, #{createdAt}
            )
            ON CONFLICT (task_no, stage)
            WHERE chunk_id IS NULL
            DO UPDATE SET
                status = 'running',
                attempt_count = EXCLUDED.attempt_count,
                max_attempts = EXCLUDED.max_attempts,
                input_message = EXCLUDED.input_message,
                input_ref = EXCLUDED.input_ref,
                error_message = NULL,
                started_at = EXCLUDED.started_at,
                finished_at = NULL,
                updated_at = EXCLUDED.updated_at
            """)
    void upsertTaskStageRunning(
            @Param("taskNo") String taskNo,
            @Param("stage") String stage,
            @Param("attemptCount") Integer attemptCount,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("inputMessageJson") String inputMessageJson,
            @Param("inputRefJson") String inputRefJson,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("createdAt") LocalDateTime createdAt);

    @Update("""
            UPDATE ocr_task_stage
            SET status = 'finished',
                output_ref = CAST(#{outputRefJson} AS jsonb),
                output_message = CAST(#{outputMessageJson} AS jsonb),
                metrics = CAST(#{metricsJson} AS jsonb),
                error_message = NULL,
                finished_at = #{finishedAt},
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
              AND stage = #{stage}
              AND chunk_id IS NULL
            """)
    void finishTaskStage(
            @Param("taskNo") String taskNo,
            @Param("stage") String stage,
            @Param("outputRefJson") String outputRefJson,
            @Param("outputMessageJson") String outputMessageJson,
            @Param("metricsJson") String metricsJson,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE ocr_task_stage
            SET status = 'failed',
                error_message = #{errorMessage},
                finished_at = #{finishedAt},
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
              AND stage = #{stage}
              AND chunk_id IS NULL
            """)
    void failTaskStage(
            @Param("taskNo") String taskNo,
            @Param("stage") String stage,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
