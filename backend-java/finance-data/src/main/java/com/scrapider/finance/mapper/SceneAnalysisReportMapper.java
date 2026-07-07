package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.config.JsonbTypeHandler;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportTargetDTO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SceneAnalysisReportMapper extends BaseMapper<SceneAnalysisReportPO> {

    @Insert("""
            INSERT INTO scene_analysis_report (
                task_id, task_no, target_type, target_code, target_name,
                report_type, generation_type, version_no, status,
                created_at, updated_at
            )
            VALUES (
                #{report.taskId}, #{report.taskNo}, #{report.targetType}, #{report.targetCode}, #{report.targetName},
                #{report.reportType}, #{report.generationType}, #{report.versionNo}, #{report.status},
                #{report.createdAt}, #{report.updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "report.id", keyColumn = "id")
    void insertReport(@Param("report") SceneAnalysisReportPO report);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM scene_analysis_report
            WHERE task_id = #{taskId}
            """)
    Integer maxVersionNo(@Param("taskId") Long taskId);

    @Select("""
            SELECT *
            FROM scene_analysis_report
            WHERE task_no = #{taskNo}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    @Results(id = "sceneAnalysisReportMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_id", property = "taskId"),
            @Result(column = "task_no", property = "taskNo"),
            @Result(column = "target_type", property = "targetType"),
            @Result(column = "target_code", property = "targetCode"),
            @Result(column = "target_name", property = "targetName"),
            @Result(column = "report_type", property = "reportType"),
            @Result(column = "generation_type", property = "generationType"),
            @Result(column = "version_no", property = "versionNo"),
            @Result(column = "status", property = "status"),
            @Result(column = "report_content", property = "reportContent", typeHandler = JsonbTypeHandler.class),
            @Result(column = "report_text", property = "reportText"),
            @Result(column = "model", property = "model"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "generated_at", property = "generatedAt"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    SceneAnalysisReportPO latestByTaskNo(@Param("taskNo") String taskNo);

    @Select("""
            SELECT COUNT(*)
            FROM scene_analysis_report r
            JOIN scene_analysis_task t ON t.id = r.task_id
            WHERE t.user_id = #{userId}
              AND r.generation_type = #{generationType}
              AND r.created_at >= #{startTime}
              AND r.created_at < #{endTime}
            """)
    long countByUserAndGenerationTypeBetween(
            @Param("userId") Long userId,
            @Param("generationType") String generationType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            WITH ranked AS (
                SELECT
                    r.*,
                    COUNT(*) OVER (PARTITION BY r.target_type, r.target_code) AS report_count,
                    ROW_NUMBER() OVER (
                        PARTITION BY r.target_type, r.target_code
                        ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
                    ) AS row_num
                FROM scene_analysis_report r
                JOIN scene_analysis_task t ON t.id = r.task_id
                <where>
                  <if test="ownerUserId != null">
                    t.user_id = #{ownerUserId}
                  </if>
                </where>
            )
            SELECT COUNT(*)
            FROM ranked
            WHERE row_num = 1
            <if test="targetName != null and targetName != ''">
              AND LOWER(COALESCE(target_name, '')) LIKE CONCAT('%', LOWER(#{targetName}), '%')
            </if>
            <if test="targetCode != null and targetCode != ''">
              AND LOWER(target_code) LIKE CONCAT('%', LOWER(#{targetCode}), '%')
            </if>
            <if test="targetType != null and targetType != ''">
              AND target_type = #{targetType}
            </if>
            </script>
            """)
    Long countTargets(
            @Param("targetName") String targetName,
            @Param("targetCode") String targetCode,
            @Param("targetType") String targetType,
            @Param("ownerUserId") Long ownerUserId);

    @Select("""
            <script>
            WITH ranked AS (
                SELECT
                    r.*,
                    COUNT(*) OVER (PARTITION BY r.target_type, r.target_code) AS report_count,
                    ROW_NUMBER() OVER (
                        PARTITION BY r.target_type, r.target_code
                        ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
                    ) AS row_num
                FROM scene_analysis_report r
                JOIN scene_analysis_task t ON t.id = r.task_id
                <where>
                  <if test="ownerUserId != null">
                    t.user_id = #{ownerUserId}
                  </if>
                </where>
            )
            SELECT *
            FROM (
                SELECT
                    target_type,
                    target_code,
                    target_name,
                    id AS latest_report_id,
                    task_no AS latest_task_no,
                    status AS latest_status,
                    report_type AS latest_report_type,
                    generation_type AS latest_generation_type,
                    version_no AS latest_version_no,
                    model AS latest_model,
                    report_text AS latest_report_text,
                    generated_at AS latest_generated_at,
                    created_at AS latest_created_at,
                    COALESCE(generated_at, created_at) AS latest_sort_at,
                    report_count
                FROM ranked
                WHERE row_num = 1
                <if test="targetName != null and targetName != ''">
                  AND LOWER(COALESCE(target_name, '')) LIKE CONCAT('%', LOWER(#{targetName}), '%')
                </if>
                <if test="targetCode != null and targetCode != ''">
                  AND LOWER(target_code) LIKE CONCAT('%', LOWER(#{targetCode}), '%')
                </if>
                <if test="targetType != null and targetType != ''">
                  AND target_type = #{targetType}
                </if>
            ) latest
            ORDER BY latest_sort_at DESC, latest_report_id DESC
            LIMIT #{limit}
            OFFSET #{offset}
            </script>
            """)
    @Results(id = "sceneAnalysisReportTargetMap", value = {
            @Result(column = "target_type", property = "targetType"),
            @Result(column = "target_code", property = "targetCode"),
            @Result(column = "target_name", property = "targetName"),
            @Result(column = "latest_report_id", property = "latestReportId"),
            @Result(column = "latest_task_no", property = "latestTaskNo"),
            @Result(column = "latest_status", property = "latestStatus"),
            @Result(column = "latest_report_type", property = "latestReportType"),
            @Result(column = "latest_generation_type", property = "latestGenerationType"),
            @Result(column = "latest_version_no", property = "latestVersionNo"),
            @Result(column = "latest_model", property = "latestModel"),
            @Result(column = "latest_report_text", property = "latestReportText"),
            @Result(column = "latest_generated_at", property = "latestGeneratedAt"),
            @Result(column = "latest_created_at", property = "latestCreatedAt"),
            @Result(column = "report_count", property = "reportCount")
    })
    List<SceneAnalysisReportTargetDTO> listTargets(
            @Param("targetName") String targetName,
            @Param("targetCode") String targetCode,
            @Param("targetType") String targetType,
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT
                r.id AS report_id,
                r.task_no,
                r.target_type,
                r.target_code,
                r.target_name,
                r.report_type,
                r.generation_type,
                r.version_no,
                r.status,
                r.model,
                r.error_message,
                r.generated_at,
                r.created_at
            FROM scene_analysis_report r
            JOIN scene_analysis_task t ON t.id = r.task_id
            WHERE r.target_type = #{targetType}
              AND r.target_code = #{targetCode}
            <if test="ownerUserId != null">
              AND t.user_id = #{ownerUserId}
            </if>
            ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
            </script>
            """)
    @Results(id = "sceneAnalysisReportHistoryMap", value = {
            @Result(column = "report_id", property = "reportId"),
            @Result(column = "task_no", property = "taskNo"),
            @Result(column = "target_type", property = "targetType"),
            @Result(column = "target_code", property = "targetCode"),
            @Result(column = "target_name", property = "targetName"),
            @Result(column = "report_type", property = "reportType"),
            @Result(column = "generation_type", property = "generationType"),
            @Result(column = "version_no", property = "versionNo"),
            @Result(column = "status", property = "status"),
            @Result(column = "model", property = "model"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "generated_at", property = "generatedAt"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<SceneAnalysisReportHistoryDTO> listHistory(
            @Param("targetType") String targetType,
            @Param("targetCode") String targetCode,
            @Param("ownerUserId") Long ownerUserId);

    @Select("""
            <script>
            SELECT r.*
            FROM scene_analysis_report r
            JOIN scene_analysis_task t ON t.id = r.task_id
            WHERE r.id = #{reportId}
            <if test="ownerUserId != null">
              AND t.user_id = #{ownerUserId}
            </if>
            </script>
            """)
    @ResultMap("sceneAnalysisReportMap")
    SceneAnalysisReportPO findByIdForOwner(
            @Param("reportId") Long reportId,
            @Param("ownerUserId") Long ownerUserId);

    @Select("""
            <script>
            SELECT r.*
            FROM scene_analysis_report r
            JOIN scene_analysis_task t ON t.id = r.task_id
            WHERE r.target_type = #{targetType}
              AND r.target_code = #{targetCode}
              AND r.status = #{status}
            <if test="ownerUserId != null">
              AND t.user_id = #{ownerUserId}
            </if>
            ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    @ResultMap("sceneAnalysisReportMap")
    List<SceneAnalysisReportPO> listLatestByTargetAndStatus(
            @Param("targetType") String targetType,
            @Param("targetCode") String targetCode,
            @Param("status") String status,
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
                r.id AS report_id,
                r.task_no,
                r.target_type,
                r.target_code,
                r.target_name,
                r.report_type,
                r.generation_type,
                r.version_no,
                r.status,
                r.generated_at,
                r.created_at
            FROM scene_analysis_report r
            JOIN scene_analysis_task t ON t.id = r.task_id
            WHERE r.status = #{status}
            <if test="targetType != null and targetType != ''">
              AND r.target_type = #{targetType}
            </if>
            <if test="ownerUserId != null">
              AND t.user_id = #{ownerUserId}
            </if>
            ORDER BY COALESCE(r.generated_at, r.created_at) DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    @ResultMap("sceneAnalysisReportHistoryMap")
    List<SceneAnalysisReportHistoryDTO> listLatestByStatus(
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit);

    @Update("""
            UPDATE scene_analysis_report
            SET status = #{status},
                report_content = CAST(#{reportContentJson} AS jsonb),
                report_text = #{reportText},
                model = #{model},
                error_message = NULL,
                generated_at = #{generatedAt},
                updated_at = #{updatedAt}
            WHERE id = #{reportId}
            """)
    void markSuccess(
            @Param("reportId") Long reportId,
            @Param("status") String status,
            @Param("reportContentJson") String reportContentJson,
            @Param("reportText") String reportText,
            @Param("model") String model,
            @Param("generatedAt") LocalDateTime generatedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE scene_analysis_report
            SET status = #{status},
                error_message = #{errorMessage},
                updated_at = #{updatedAt}
            WHERE id = #{reportId}
            """)
    void markFailed(
            @Param("reportId") Long reportId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") LocalDateTime updatedAt);
}
