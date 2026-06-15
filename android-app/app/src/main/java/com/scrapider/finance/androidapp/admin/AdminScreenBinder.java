package com.scrapider.finance.androidapp.admin;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import java.util.Locale;

public final class AdminScreenBinder {
    private static final String SCREEN = "系统管理";

    public void apply(RuntimeValueStore store, AdminSummary summary) {
        store.put(SCREEN, "管理标签", "数据同步", blank(summary.latestSyncStatus));
        store.putTone(SCREEN, "管理标签", "数据同步", syncTone(summary.latestSyncStatus));
        store.put(SCREEN, "系统监控", "访问量", String.valueOf(summary.totalVisitCount));
        store.put(SCREEN, "系统监控", "用户数", String.valueOf(summary.userCount));
        store.put(SCREEN, "系统监控", "智能调用", String.valueOf(summary.tokenRequestCount));
        store.put(SCREEN, "系统监控", "同步状态", blank(summary.latestSyncStatus));
        store.putTone(SCREEN, "系统监控", "同步状态", syncTone(summary.latestSyncStatus));
        store.put(SCREEN, "调用用量", "总费用", money(summary));
        store.put(SCREEN, "调用用量", "请求数", String.valueOf(summary.tokenRequestCount));
        store.put(SCREEN, "数据同步", "股票全量同步", syncProgress(summary, "STOCK"));
        store.put(SCREEN, "数据同步", "指数全量同步", syncProgress(summary, "INDEX"));
        store.put(SCREEN, "数据同步", "可转债同步", syncProgress(summary, "BOND"));
        store.put(SCREEN, "同步状态", "最近任务", latestJob(summary));
        store.put(SCREEN, "同步状态", "失败原因", summary.latestSyncError.isEmpty() ? "暂无失败原因" : summary.latestSyncError);
        store.putTone(SCREEN, "同步状态", "失败原因", summary.latestSyncError.isEmpty() ? "success" : "danger");
    }

    public String statusMessage(ApiResult result, AdminSummary summary) {
        if (result.success) {
            return "系统管理已同步：访问 " + summary.totalVisitCount
                    + " 次，智能调用 " + summary.tokenRequestCount
                    + " 次，同步任务 " + summary.syncJobCount + " 个。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用系统管理数据和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private String money(AdminSummary summary) {
        return summary.currency + " " + String.format(Locale.CHINA, "%.2f", summary.totalCost);
    }

    private String latestJob(AdminSummary summary) {
        if (summary.syncJobCount <= 0) {
            return "暂无全量同步任务";
        }
        return summary.latestSyncType + " 全量同步：" + blank(summary.latestSyncStatus);
    }

    private String syncProgress(AdminSummary summary, String type) {
        if (type.equals(summary.latestSyncType)) {
            return "success".equals(summary.latestSyncStatus) ? "100%" : "running".equals(summary.latestSyncStatus) ? "70%" : "30%";
        }
        return "0%";
    }

    private String syncTone(String status) {
        if ("success".equals(status)) {
            return "success";
        }
        if ("failed".equals(status)) {
            return "danger";
        }
        if (status == null || status.isEmpty()) {
            return "muted";
        }
        return "amber";
    }

    private String blank(String value) {
        return value == null || value.isEmpty() ? "未返回" : value;
    }
}
