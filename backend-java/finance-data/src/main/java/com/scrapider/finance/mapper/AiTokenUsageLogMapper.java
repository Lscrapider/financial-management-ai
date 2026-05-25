package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.dto.AiTokenUsageSummaryDTO;
import com.scrapider.finance.domain.dto.AiTokenUsageTrendDTO;
import com.scrapider.finance.domain.po.AiTokenUsageLogPO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiTokenUsageLogMapper extends BaseMapper<AiTokenUsageLogPO> {

    @Select("""
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
            """)
    AiTokenUsageSummaryDTO summarySince(@Param("startTime") LocalDateTime startTime);

    @Select("""
            SELECT
                date_trunc('day', occurred_at) AS time_bucket,
                COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS request_count
            FROM ai_token_usage_log
            WHERE occurred_at >= #{startTime}
            GROUP BY date_trunc('day', occurred_at)
            ORDER BY time_bucket ASC
            """)
    List<AiTokenUsageTrendDTO> trendSince(@Param("startTime") LocalDateTime startTime);
}
