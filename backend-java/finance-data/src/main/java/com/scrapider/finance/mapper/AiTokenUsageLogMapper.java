package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.dto.AiTokenUsageCostSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageTrendDTO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiTokenUsageLogMapper extends BaseMapper<AiTokenUsageLogPO> {

    @Select("""
            <script>
            SELECT
                COUNT(*) AS request_count,
                COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COALESCE(SUM(cached_tokens), 0) AS cached_tokens,
                COALESCE(SUM(reasoning_tokens), 0) AS reasoning_tokens,
                MAX(occurred_at) AS latest_occurred_at
            FROM ai_token_usage_log
            WHERE occurred_at >= #{startTime}
              <if test="endTime != null">
                AND occurred_at &lt;= #{endTime}
              </if>
              <if test="source != null">
                AND source = #{source}
              </if>
              <if test="phase != null">
                AND phase = #{phase}
              </if>
              <if test="model != null">
                AND model = #{model}
              </if>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
                </foreach>
              </if>
            </script>
            """)
    AiTokenUsageSummaryDTO summary(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("phase") String phase,
            @Param("model") String model,
            @Param("userIds") Collection<Long> userIds);

    @Select("""
            <script>
            SELECT
                model,
                COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(cached_tokens), 0) AS cached_tokens,
                COALESCE(SUM(prompt_cache_hit_tokens), 0) AS prompt_cache_hit_tokens,
                COALESCE(SUM(prompt_cache_miss_tokens), 0) AS prompt_cache_miss_tokens
            FROM ai_token_usage_log
            WHERE occurred_at >= #{startTime}
              <if test="endTime != null">
                AND occurred_at &lt;= #{endTime}
              </if>
              <if test="source != null">
                AND source = #{source}
              </if>
              <if test="phase != null">
                AND phase = #{phase}
              </if>
              <if test="model != null">
                AND model = #{model}
              </if>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
                </foreach>
              </if>
            GROUP BY model
            </script>
            """)
    List<AiTokenUsageCostSummaryDTO> costSummary(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("phase") String phase,
            @Param("model") String model,
            @Param("userIds") Collection<Long> userIds);

    @Select("""
            <script>
            SELECT
                date_trunc('day', occurred_at) AS time_bucket,
                COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS request_count
            FROM ai_token_usage_log
            WHERE occurred_at >= #{startTime}
              <if test="endTime != null">
                AND occurred_at &lt;= #{endTime}
              </if>
              <if test="source != null">
                AND source = #{source}
              </if>
              <if test="phase != null">
                AND phase = #{phase}
              </if>
              <if test="model != null">
                AND model = #{model}
              </if>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
                </foreach>
              </if>
            GROUP BY date_trunc('day', occurred_at)
            ORDER BY time_bucket ASC
            </script>
            """)
    List<AiTokenUsageTrendDTO> trend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("phase") String phase,
            @Param("model") String model,
            @Param("userIds") Collection<Long> userIds);
}
