package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.ChangePasswordParam;
import com.scrapider.finance.domain.param.LoginParam;
import com.scrapider.finance.domain.param.RegisterParam;
import com.scrapider.finance.domain.param.UpdateNotificationParam;
import com.scrapider.finance.domain.param.UpdateProfileParam;
import com.scrapider.finance.domain.vo.LoginResultVO;
import com.scrapider.finance.domain.vo.UserInfoVO;
import com.scrapider.finance.security.LoginUser;

public interface AuthService {

    LoginResultVO login(LoginParam param);

    void register(RegisterParam param);

    void logout(String token);

    UserInfoVO getUserInfo(LoginUser loginUser, String token);

    String refresh(String token);

    void updateProfile(LoginUser loginUser, UpdateProfileParam param);

    void changePassword(LoginUser loginUser, ChangePasswordParam param);

    void updateNotificationSetting(LoginUser loginUser, UpdateNotificationParam param);
}
