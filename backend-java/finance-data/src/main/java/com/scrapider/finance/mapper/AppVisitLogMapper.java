package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.dto.AppVisitSummaryDTO;
import com.scrapider.finance.domain.dto.AppVisitTrendDTO;
import com.scrapider.finance.domain.po.AppVisitLogPO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppVisitLogMapper extends BaseMapper<AppVisitLogPO> {

    @Select("""
            SELECT
                COUNT(*) AS total_visit_count,
                COALESCE(SUM(CASE WHEN occurred_at >= #{startTime} THEN 1 ELSE 0 END), 0) AS period_visit_count,
                COUNT(DISTINCT username) FILTER (WHERE username IS NOT NULL AND occurred_at >= #{startTime}) AS unique_user_count,
                MAX(occurred_at) AS latest_occurred_at
            FROM app_visit_log
            """)
    AppVisitSummaryDTO summarySince(@Param("startTime") LocalDateTime startTime);

    @Select("""
            SELECT
                date_trunc('hour', occurred_at) AS time_bucket,
                COUNT(*) AS visit_count,
                COUNT(DISTINCT username) FILTER (WHERE username IS NOT NULL) AS unique_user_count
            FROM app_visit_log
            WHERE occurred_at >= #{startTime}
            GROUP BY date_trunc('hour', occurred_at)
            ORDER BY time_bucket ASC
            """)
    List<AppVisitTrendDTO> trendSince(@Param("startTime") LocalDateTime startTime);
}
