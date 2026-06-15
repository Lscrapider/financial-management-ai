package com.scrapider.finance.androidapp.auth;

import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class AuthPageBinder {
    private static final String ROLE_BLOCK = "角色与入口";
    private static final String ADMIN_ROLE = "管理员";
    private static final String USER_ROLE = "普通用户";

    public void applyLoginRole(RuntimeValueStore store, boolean loginAsAdmin) {
        store.put(AuthPageSpecFactory.SCREEN_TITLE, ROLE_BLOCK, ADMIN_ROLE, loginAsAdmin ? "已选" : "可切换");
        store.putTone(AuthPageSpecFactory.SCREEN_TITLE, ROLE_BLOCK, ADMIN_ROLE, loginAsAdmin ? "blue" : "muted");
        store.put(AuthPageSpecFactory.SCREEN_TITLE, ROLE_BLOCK, USER_ROLE, loginAsAdmin ? "可切换" : "已选");
        store.putTone(AuthPageSpecFactory.SCREEN_TITLE, ROLE_BLOCK, USER_ROLE, loginAsAdmin ? "muted" : "blue");
    }
}
