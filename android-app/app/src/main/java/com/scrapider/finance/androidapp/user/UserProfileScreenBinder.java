package com.scrapider.finance.androidapp.user;

import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;
import com.scrapider.finance.androidapp.session.SessionState;

public final class UserProfileScreenBinder {
    private static final String SCREEN = "我的与智能研究助手";

    public void apply(RuntimeValueStore store, SessionState state) {
        store.put(SCREEN, "个人资料", "用户名", state.username.isEmpty() ? "未登录" : state.username + "（不可编辑）");
        store.put(SCREEN, "个人资料", "姓名", state.realName.isEmpty() ? state.displayName() : state.realName);
        store.put(SCREEN, "个人资料", "邮箱", state.email.isEmpty() ? "未绑定" : state.email);
        store.putTone(SCREEN, "个人资料", "邮箱", state.email.isEmpty() ? "amber" : "success");
        store.put(SCREEN, "个人资料", "手机", state.phone.isEmpty() ? "未绑定" : state.phone);
        store.putTone(SCREEN, "个人资料", "手机", state.phone.isEmpty() ? "amber" : "success");
        store.put(SCREEN, "个人资料", "资料更新时间", state.authenticated ? "已同步后端用户信息" : "未登录");
        store.put(SCREEN, "安全与通知", "邮件通知", state.emailNotification ? "已开启" : "未开启");
        store.putTone(SCREEN, "安全与通知", "邮件通知", state.emailNotification ? "success" : "amber");
        store.put(SCREEN, "智能研究助手", "心理画像", state.authenticated ? "已使用登录态" : "需登录");
        store.putTone(SCREEN, "智能研究助手", "心理画像", state.authenticated ? "blue" : "amber");
        store.put(SCREEN, "对话", "模型", "研究助手模型，等待实时对话通道");
        store.put(SCREEN, "对话", "流式状态", state.authenticated ? "已保留登录态，可申请对话凭证" : "需登录后申请对话凭证");
        store.putTone(SCREEN, "对话", "流式状态", state.authenticated ? "success" : "amber");
        store.put(SCREEN, "对话", "安全口径", "提供证据和解释，不给出买卖指令");
    }
}
