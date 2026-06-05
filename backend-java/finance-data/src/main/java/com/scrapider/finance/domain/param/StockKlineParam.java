package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class StockKlineParam {

    private String stockCode;
    private String secid;
    private String periodType = "daily";
    private String adjustType = "hfq";
    private String startDate;
    private String endDate;
    private Integer limit = 250;
}
