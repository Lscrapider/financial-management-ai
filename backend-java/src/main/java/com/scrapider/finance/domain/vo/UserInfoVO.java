package com.scrapider.finance.domain.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfoVO {

    private String userId;
    private String username;
    private String realName;
    private String avatar;
    private List<String> roles;
    private String desc;
    private String homePath;
    private String token;
}
