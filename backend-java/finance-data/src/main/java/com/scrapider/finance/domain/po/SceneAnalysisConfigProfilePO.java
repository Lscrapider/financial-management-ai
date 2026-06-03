package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "scene_analysis_config_profile", autoResultMap = true)
public class SceneAnalysisConfigProfilePO {

    private Long id;
    private Long userId;
    private String name;
    private String configGroup;
    private String configProfile;
    private String targetType;
    private String reportType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode configJson;

    private Boolean systemDefault;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SceneAnalysisConfigProfilePO createCustom(
            Long userId,
            String name,
            String configGroup,
            String configProfile,
            String targetType,
            String reportType,
            JsonNode configJson) {
        LocalDateTime now = LocalDateTime.now();
        SceneAnalysisConfigProfilePO profile = new SceneAnalysisConfigProfilePO();
        profile.setUserId(userId);
        profile.setName(name);
        profile.setConfigGroup(configGroup);
        profile.setConfigProfile(configProfile);
        profile.setTargetType(targetType);
        profile.setReportType(reportType);
        profile.setConfigJson(configJson);
        profile.setSystemDefault(false);
        profile.setEnabled(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profile;
    }
}
