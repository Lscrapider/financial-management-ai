package com.scrapider.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scrapider.finance.domain.constant.AuthConstant;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.mapper.AppUserMapper;
import com.scrapider.finance.security.LoginUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserMapper appUserMapper;

    public AppUserDetailsService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserPO user = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUserPO>().eq(AppUserPO::getUsername, username).last("LIMIT 1"));
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        if (!AuthConstant.isSupportedRoleCode(user.getRoleCode())) {
            throw new UsernameNotFoundException("User role is invalid");
        }
        return new LoginUser(user);
    }
}
