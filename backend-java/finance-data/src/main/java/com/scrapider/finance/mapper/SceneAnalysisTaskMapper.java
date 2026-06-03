package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SceneAnalysisTaskMapper extends BaseMapper<SceneAnalysisTaskPO> {

    @Insert("""
            INSERT INTO scene_analysis_task (
                task_no, user_id, target_type, target_code, target_name,
                report_type, config_profile, config_snapshot, status,
                submitted_at, created_at, updated_at
            )
            VALUES (
                #{taskNo}, #{userId}, #{targetType}, #{targetCode}, #{targetName},
                #{reportType}, #{configProfile}, CAST(#{configSnapshotJson} AS jsonb), #{status},
                #{submittedAt}, #{createdAt}, #{updatedAt}
            )
            """)
    void insertTask(
            @Param("taskNo") String taskNo,
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetCode") String targetCode,
            @Param("targetName") String targetName,
            @Param("reportType") String reportType,
            @Param("configProfile") String configProfile,
            @Param("configSnapshotJson") String configSnapshotJson,
            @Param("status") String status,
            @Param("submittedAt") LocalDateTime submittedAt,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE scene_analysis_task
            SET status = #{status},
                current_scenes_payload = CAST(#{currentScenesPayloadJson} AS jsonb),
                error_message = NULL,
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void markCurrentScenesReady(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("currentScenesPayloadJson") String currentScenesPayloadJson,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE scene_analysis_task
            SET status = #{status},
                error_message = NULL,
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void updateStatus(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE scene_analysis_task
            SET status = #{status},
                report_payload = CAST(#{reportPayloadJson} AS jsonb),
                error_message = NULL,
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void saveReportPayload(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("reportPayloadJson") String reportPayloadJson,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE scene_analysis_task
            SET status = #{status},
                error_message = NULL,
                finished_at = #{finishedAt},
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void markReportSucceeded(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
