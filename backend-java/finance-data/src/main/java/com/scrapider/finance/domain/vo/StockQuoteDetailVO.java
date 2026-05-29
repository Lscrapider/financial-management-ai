package com.scrapider.finance.domain.vo;

import lombok.Data;

@Data
public class StockQuoteDetailVO {

    private Integer fieldIndex;
    private String fieldName;
    private String fieldValue;

    public static StockQuoteDetailVO of(Integer fieldIndex, String fieldName, String fieldValue) {
        StockQuoteDetailVO vo = new StockQuoteDetailVO();
        vo.setFieldIndex(fieldIndex);
        vo.setFieldName(fieldName);
        vo.setFieldValue(fieldValue);
        return vo;
    }
}
