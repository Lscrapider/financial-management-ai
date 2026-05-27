package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class UpdateProfileParam {

    private String realName;
    private String introduction;
    private String email;
    private String phone;
}
