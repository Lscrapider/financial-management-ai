package com.scrapider.finance.domain.param;

import lombok.Data;

@Data
public class BondQuoteListParam {

    private String marketCode;
    private Integer limit = 100;
    private String sortField;
    private String sortOrder;
}
