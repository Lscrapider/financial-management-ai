package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class RegisterParam {

    private String username;
    private String password;
    private String confirmPassword;
}
