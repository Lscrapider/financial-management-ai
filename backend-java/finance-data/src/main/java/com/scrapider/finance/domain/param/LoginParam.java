package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class LoginParam {

    private String username;
    private String password;
    private String roleCode;
}
