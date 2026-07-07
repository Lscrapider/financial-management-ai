package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.config.JsonbTypeHandler;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.param.RegisterParam;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "app_user", autoResultMap = true)
public class AppUserPO {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String roleCode;
    private String avatar;
    private Boolean enabled;
    private String homePath;
    private String introduction;
    private String email;
    private String phone;
    private Boolean emailNotification;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode agentExecutionBudgetJson;

    private Integer aiDailyReportLimit;
    private Integer aiDailyChatLimit;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AppUserPO fromRegisterParam(RegisterParam param, String encodedPassword) {
        AppUserPO user = new AppUserPO();
        user.setUsername(param.getUsername());
        user.setPassword(encodedPassword);
        user.setRealName(param.getUsername());
        user.setRoleCode(AuthConstant.DEFAULT_ROLE_CODE);
        user.setAvatar(AuthConstant.DEFAULT_AVATAR);
        user.setEnabled(true);
        user.setHomePath(AuthConstant.DEFAULT_HOME_PATH);
        user.setEmailNotification(true);
        user.setAiDailyReportLimit(AuthConstant.DEFAULT_AI_DAILY_REPORT_LIMIT);
        user.setAiDailyChatLimit(AuthConstant.DEFAULT_AI_DAILY_CHAT_LIMIT);
        return user;
    }
}
