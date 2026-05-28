package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("watch_group")
public class WatchGroupPO {

    private Long id;
    private Long userId;
    private String groupName;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WatchGroupPO create(Long userId, String groupName, Integer sortOrder) {
        LocalDateTime now = LocalDateTime.now();
        WatchGroupPO group = new WatchGroupPO();
        group.setUserId(userId);
        group.setGroupName(groupName);
        group.setSortOrder(sortOrder == null ? 0 : sortOrder);
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        return group;
    }
}
