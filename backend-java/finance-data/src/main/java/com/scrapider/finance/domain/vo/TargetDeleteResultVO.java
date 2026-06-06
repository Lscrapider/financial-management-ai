package com.scrapider.finance.domain.vo;

import lombok.Data;

@Data
public class TargetDeleteResultVO {

    private String targetType;
    private String targetCode;
    private Boolean deleted;

    public static TargetDeleteResultVO of(String targetType, String targetCode) {
        TargetDeleteResultVO result = new TargetDeleteResultVO();
        result.setTargetType(targetType);
        result.setTargetCode(targetCode);
        result.setDeleted(true);
        return result;
    }
}
