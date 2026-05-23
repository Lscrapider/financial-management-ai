package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("index_config")
public class IndexConfigPO {

    private Long id;
    private String indexName;
    private String indexCode;
    private String marketCode;
    private String exchangeCode;
    private String secid;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
