package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_user")
public class AppUserPO {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String roleCode;
    private String avatar;
    private Boolean enabled;
    private String homePath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
