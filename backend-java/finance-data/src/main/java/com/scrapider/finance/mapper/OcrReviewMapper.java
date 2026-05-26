package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.OcrReviewPO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OcrReviewMapper extends BaseMapper<OcrReviewPO> {

    @Insert("""
            INSERT INTO ocr_review (
                task_no, status, cleaned_ref, draft_content,
                overall_confidence, paragraph_count, warning_count,
                created_at, updated_at
            )
            VALUES (
                #{taskNo}, #{status}, CAST(#{cleanedRefJson} AS jsonb), CAST(#{draftContentJson} AS jsonb),
                #{overallConfidence}, #{paragraphCount}, #{warningCount},
                #{createdAt}, #{updatedAt}
            )
            ON CONFLICT (task_no)
            DO UPDATE SET
                status = EXCLUDED.status,
                cleaned_ref = EXCLUDED.cleaned_ref,
                draft_content = EXCLUDED.draft_content,
                overall_confidence = EXCLUDED.overall_confidence,
                paragraph_count = EXCLUDED.paragraph_count,
                warning_count = EXCLUDED.warning_count,
                updated_at = EXCLUDED.updated_at
            WHERE ocr_review.status = 'pending'
            """)
    void upsertPending(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("cleanedRefJson") String cleanedRefJson,
            @Param("draftContentJson") String draftContentJson,
            @Param("overallConfidence") BigDecimal overallConfidence,
            @Param("paragraphCount") Integer paragraphCount,
            @Param("warningCount") Integer warningCount,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE ocr_review
            SET status = #{status},
                draft_content = CAST(#{draftContentJson} AS jsonb),
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void updateDraft(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("draftContentJson") String draftContentJson,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE ocr_review
            SET status = #{status},
                reviewed_ref = CAST(#{reviewedRefJson} AS jsonb),
                reviewed_at = #{reviewedAt},
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    void approve(
            @Param("taskNo") String taskNo,
            @Param("status") String status,
            @Param("reviewedRefJson") String reviewedRefJson,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
