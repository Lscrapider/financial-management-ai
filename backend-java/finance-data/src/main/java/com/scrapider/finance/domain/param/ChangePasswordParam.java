package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class ChangePasswordParam {

    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
