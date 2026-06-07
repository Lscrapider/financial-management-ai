package com.scrapider.finance.converter;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.AppUserPO;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import java.util.List;

public final class AuthConverter {

    private AuthConverter() {
    }

    public static LoginResultVO toLoginResult(String token) {
        return new LoginResultVO(token);
    }

    public static UserInfoVO toUserInfo(AppUserPO user, String roleCode, String token) {
        List<String> roles = StrUtil.isBlank(roleCode) ? List.of() : List.of(roleCode);
        return new UserInfoVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRealName(),
                user.getAvatar(),
                roles,
                user.getIntroduction(),
                user.getHomePath(),
                token,
                user.getIntroduction(),
                user.getEmail(),
                user.getPhone(),
                user.getEmailNotification());
    }
}
