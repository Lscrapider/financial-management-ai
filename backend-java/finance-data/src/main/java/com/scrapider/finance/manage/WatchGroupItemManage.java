package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.WatchGroupItemPO;
import com.scrapider.finance.mapper.WatchGroupItemMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WatchGroupItemManage extends ServiceImpl<WatchGroupItemMapper, WatchGroupItemPO> {

    public List<WatchGroupItemPO> listByGroupIds(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(WatchGroupItemPO::getGroupId, groupIds)
                .orderByAsc(WatchGroupItemPO::getTargetType)
                .orderByAsc(WatchGroupItemPO::getSortOrder)
                .orderByAsc(WatchGroupItemPO::getId)
                .list();
    }

    public List<WatchGroupItemPO> listByGroupId(Long groupId) {
        return this.lambdaQuery()
                .eq(WatchGroupItemPO::getGroupId, groupId)
                .orderByAsc(WatchGroupItemPO::getTargetType)
                .orderByAsc(WatchGroupItemPO::getSortOrder)
                .orderByAsc(WatchGroupItemPO::getId)
                .list();
    }

    public WatchGroupItemPO findByIdAndUserId(Long id, Long userId) {
        return this.lambdaQuery()
                .eq(WatchGroupItemPO::getId, id)
                .eq(WatchGroupItemPO::getUserId, userId)
                .one();
    }

    public WatchGroupItemPO findDuplicate(Long groupId, String targetType, String targetCode) {
        return this.lambdaQuery()
                .eq(WatchGroupItemPO::getGroupId, groupId)
                .eq(WatchGroupItemPO::getTargetType, targetType)
                .eq(WatchGroupItemPO::getTargetCode, targetCode)
                .one();
    }

    public int nextSortOrder(Long groupId) {
        return this.listByGroupId(groupId).stream()
                .map(WatchGroupItemPO::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(0);
    }

    public boolean updateItem(WatchGroupItemPO item) {
        item.setUpdatedAt(LocalDateTime.now());
        return this.updateById(item);
    }
}
