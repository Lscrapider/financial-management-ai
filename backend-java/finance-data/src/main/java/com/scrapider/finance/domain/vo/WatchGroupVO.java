package com.scrapider.finance.domain.vo;

import com.scrapider.finance.domain.po.WatchGroupPO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WatchGroupVO {

    private String id;
    private String groupName;
    private Integer sortOrder;
    private List<WatchGroupItemVO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WatchGroupVO fromPO(WatchGroupPO group, List<WatchGroupItemVO> items) {
        WatchGroupVO vo = new WatchGroupVO();
        vo.setId(String.valueOf(group.getId()));
        vo.setGroupName(group.getGroupName());
        vo.setSortOrder(group.getSortOrder());
        vo.setItems(items);
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());
        return vo;
    }
}
