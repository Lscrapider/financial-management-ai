package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.WatchGroupPO;
import com.scrapider.finance.mapper.WatchGroupMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WatchGroupManage extends ServiceImpl<WatchGroupMapper, WatchGroupPO> {

    public List<WatchGroupPO> listByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(WatchGroupPO::getUserId, userId)
                .orderByAsc(WatchGroupPO::getSortOrder)
                .orderByAsc(WatchGroupPO::getId)
                .list();
    }

    public WatchGroupPO findByIdAndUserId(Long id, Long userId) {
        return this.lambdaQuery()
                .eq(WatchGroupPO::getId, id)
                .eq(WatchGroupPO::getUserId, userId)
                .one();
    }

    public WatchGroupPO findByUserIdAndName(Long userId, String groupName) {
        return this.lambdaQuery()
                .eq(WatchGroupPO::getUserId, userId)
                .eq(WatchGroupPO::getGroupName, groupName)
                .one();
    }

    public int nextSortOrder(Long userId) {
        return this.listByUserId(userId).stream()
                .map(WatchGroupPO::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(0);
    }

    public boolean rename(Long id, Long userId, String groupName) {
        return this.lambdaUpdate()
                .eq(WatchGroupPO::getId, id)
                .eq(WatchGroupPO::getUserId, userId)
                .set(WatchGroupPO::getGroupName, groupName)
                .set(WatchGroupPO::getUpdatedAt, LocalDateTime.now())
                .update();
    }
}
