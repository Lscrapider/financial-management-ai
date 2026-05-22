package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("stock_config")
public class StockConfigPO {

    private Long id;
    private String stockName;
    private String stockCode;
    private String marketCode;
    private String exchangeCode;
    private String secid;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
