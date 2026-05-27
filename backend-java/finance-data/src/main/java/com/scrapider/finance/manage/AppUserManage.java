package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.mapper.AppUserMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppUserManage extends ServiceImpl<AppUserMapper, AppUserPO> {

    public boolean existsByUsername(String username) {
        return this.count(new LambdaQueryWrapper<AppUserPO>().eq(AppUserPO::getUsername, username)) > 0;
    }

    public void saveUser(AppUserPO user) {
        this.save(user);
    }

    public long countEnabledUsers() {
        return this.count(new LambdaQueryWrapper<AppUserPO>().eq(AppUserPO::getEnabled, true));
    }

    public List<AppUserPO> listUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<AppUserPO>().in(AppUserPO::getId, userIds));
    }
}
