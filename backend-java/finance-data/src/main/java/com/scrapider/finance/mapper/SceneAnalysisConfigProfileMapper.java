package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SceneAnalysisConfigProfileMapper extends BaseMapper<SceneAnalysisConfigProfilePO> {

    @Insert("""
            INSERT INTO scene_analysis_config_profile (
                user_id, name, config_group, config_profile, target_type, report_type,
                config_json, system_default, enabled, created_at, updated_at
            )
            VALUES (
                #{profile.userId}, #{profile.name}, #{profile.configGroup}, #{profile.configProfile},
                #{profile.targetType}, #{profile.reportType}, CAST(#{configJson} AS jsonb),
                #{profile.systemDefault}, #{profile.enabled}, #{profile.createdAt}, #{profile.updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "profile.id")
    void insertProfile(
            @Param("profile") SceneAnalysisConfigProfilePO profile,
            @Param("configJson") String configJson);

    @Update("""
            UPDATE scene_analysis_config_profile
            SET name = #{profile.name},
                config_group = #{profile.configGroup},
                config_profile = #{profile.configProfile},
                target_type = #{profile.targetType},
                report_type = #{profile.reportType},
                config_json = CAST(#{configJson} AS jsonb),
                updated_at = #{profile.updatedAt}
            WHERE id = #{profile.id}
              AND user_id = #{profile.userId}
              AND system_default = FALSE
              AND enabled = TRUE
            """)
    int updateEditableProfile(
            @Param("profile") SceneAnalysisConfigProfilePO profile,
            @Param("configJson") String configJson);

    @Update("""
            UPDATE scene_analysis_config_profile
            SET enabled = FALSE,
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND user_id = #{userId}
              AND system_default = FALSE
              AND enabled = TRUE
            """)
    int disableEditableProfile(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
