package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("bond_config")
public class BondConfigPO {

    private Long id;
    private String bondName;
    private String bondCode;
    private String marketCode;
    private String exchangeCode;
    private String secid;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
