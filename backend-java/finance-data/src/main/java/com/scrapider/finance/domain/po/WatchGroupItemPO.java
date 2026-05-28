package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("watch_group_item")
public class WatchGroupItemPO {

    private Long id;
    private Long groupId;
    private Long userId;
    private String targetType;
    private String targetCode;
    private String targetName;
    private String secid;
    private String remark;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WatchGroupItemPO create(
            Long groupId,
            Long userId,
            String targetType,
            String targetCode,
            String targetName,
            String secid,
            String remark,
            Integer sortOrder) {
        LocalDateTime now = LocalDateTime.now();
        WatchGroupItemPO item = new WatchGroupItemPO();
        item.setGroupId(groupId);
        item.setUserId(userId);
        item.setTargetType(targetType);
        item.setTargetCode(targetCode);
        item.setTargetName(targetName);
        item.setSecid(secid);
        item.setRemark(remark);
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }
}
