package com.scrapider.finance.androidapp.auth;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AuthPageSpecFactory {
    public static final String SCREEN_TITLE = "登录与权限";
    public static final String ACTION_LOGIN = "login";
    public static final String FIELD_USERNAME = "账号";
    public static final String FIELD_PASSWORD = "密码";

    private AuthPageSpecFactory() {
    }

    public static List<ScreenSpec> create() {
        return Collections.singletonList(new ScreenSpec(
                "58c3567b46944e8fb5c51c583d095f87",
                SCREEN_TITLE,
                "我的",
                "认证栈",
                "账号登录、注册入口、暂未接入能力说明和登录后回跳。",
                false,
                false,
                Arrays.asList(
                        block("角色与入口", "chips",
                                row("管理员", "已选", "blue"),
                                row("普通用户", "可切换", "muted"),
                                row("注册入口", "创建账号", "blue"),
                                navRow("回跳目标", "投资工作台", "amber", "投资工作台")),
                        block("账号密码登录", "form",
                                row(FIELD_USERNAME, "admin", "field"),
                                row(FIELD_PASSWORD, "123456", "password"),
                                row("滑块校验", "已通过，请继续登录", "success"),
                                row("记住账号", "已开启", "switch"),
                                commandRow("登录按钮", "登录", "primary", ACTION_LOGIN)),
                        block("注册表单", "form",
                                row("新用户名", "研究员02", "field"),
                                row("密码强度", "较强，包含数字和符号", "success"),
                                row("确认密码", "两次输入一致", "password"),
                                row("协议确认", "已阅读服务条款和隐私政策", "switch"),
                                row("创建账号", "注册成功后回到登录", "primary")),
                        block("暂未接入", "list",
                                row("验证码登录", "暂未接入真实短信接口", "muted"),
                                row("扫码登录", "暂未接入扫码会话", "muted"),
                                row("忘记密码", "暂未接入重置接口", "muted"),
                                row("失败反馈", "账号或密码错误时保留当前表单", "danger"))
                )
        ));
    }

    private static BlockSpec block(String title, String type, RowSpec... rows) {
        return new BlockSpec(title, type, Arrays.asList(rows));
    }

    private static RowSpec row(String label, String value, String tone) {
        return new RowSpec(label, value, tone);
    }

    private static RowSpec navRow(String label, String value, String tone, String targetScreenTitle) {
        return new RowSpec(label, value, tone, false, targetScreenTitle, true);
    }

    private static RowSpec commandRow(String label, String value, String tone, String actionKey) {
        return new RowSpec(label, value, tone, false, null, true, actionKey);
    }
}
