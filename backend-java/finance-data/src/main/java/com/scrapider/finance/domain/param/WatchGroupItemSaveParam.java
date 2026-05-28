package com.scrapider.finance.domain.param;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WatchGroupItemSaveParam {

    private Long id;
    private Long groupId;
    private String targetType;
    private String targetCode;
    private String targetName;
    private String secid;
    private String remark;
    private BigDecimal buyPrice;
    private BigDecimal position;
}
