package com.scrapider.finance.androidapp.auth;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.session.SessionController;
import com.scrapider.finance.androidapp.session.SessionState;

public final class AuthLoginAction {
    public interface Host {
        void showLoginProgress(String message);

        void showLoginSuccess(String message);

        void showLoginFailure(String message, ApiResult result);

        void applySessionState(SessionState state);

        void openDefaultWorkbench();

        void refreshWorkbenchData();

        void renderCurrentScreenPanel();
    }

    public void execute(
            SessionController sessionController,
            String username,
            String password,
            boolean loginAsAdmin,
            Host host) {
        String roleCode = loginAsAdmin ? "admin" : "user";
        host.showLoginProgress("正在登录：" + username + "，角色 " + (loginAsAdmin ? "管理员" : "普通用户") + "。");
        sessionController.login(username, password, roleCode, (result, state) -> {
            if (result.success && state.authenticated) {
                host.applySessionState(state);
                host.openDefaultWorkbench();
                host.showLoginSuccess("登录成功：" + state.displayName() + "，当前角色 " + state.roleLabel() + "。");
                host.refreshWorkbenchData();
                return;
            }
            host.showLoginFailure("登录失败：" + result.message, result);
            host.renderCurrentScreenPanel();
        });
    }
}
