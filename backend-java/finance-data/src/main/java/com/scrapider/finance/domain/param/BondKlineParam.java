package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class BondKlineParam {

    private String bondCode;
    private String secid;
    private String periodType = "daily";
    private String startDate;
    private String endDate;
    private Integer limit = 250;
}
