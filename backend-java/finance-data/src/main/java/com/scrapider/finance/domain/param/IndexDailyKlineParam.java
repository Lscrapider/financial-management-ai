package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class IndexDailyKlineParam {

    private String indexCode;
    private String secid;
    private String startDate;
    private String endDate;
    private Integer limit = 250;
}
