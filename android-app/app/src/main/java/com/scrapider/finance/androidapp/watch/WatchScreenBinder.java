package com.scrapider.finance.androidapp.watch;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeFormatters;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

import java.util.Locale;

public final class WatchScreenBinder {
    private static final String SCREEN = "观察风控";
    private static final String[] GROUP_ROWS = {"核心持仓", "低估观察", "转债"};
    private static final String[] ITEM_ROWS = {"宁德时代", "招商银行", "平安转债"};
    private static final String[] ALERT_ROWS = {"宁德时代", "招商银行", "平安转债"};

    public void apply(RuntimeValueStore runtimeValueStore, WatchSummary summary) {
        for (int i = 0; i < GROUP_ROWS.length && i < summary.groupNames.size(); i++) {
            String row = GROUP_ROWS[i];
            String groupName = summary.groupNames.get(i);
            runtimeValueStore.putLabel(SCREEN, "分组", row, groupName);
            runtimeValueStore.put(SCREEN, "分组", row, String.valueOf(summary.groupItemCounts.get(i)));
            runtimeValueStore.putTone(SCREEN, "分组", row, i == 0 ? "blue" : "muted");
            if (i < summary.groupIds.size() && !summary.groupIds.get(i).isEmpty()) {
                runtimeValueStore.put(SCREEN, "分组ID:" + groupName, summary.groupIds.get(i));
            }
        }
        runtimeValueStore.put(SCREEN, "分组", "触发提醒", String.valueOf(summary.outAlertCount + summary.nearAlertCount));
        runtimeValueStore.putTone(SCREEN, "分组", "触发提醒", summary.outAlertCount > 0 ? "danger" : summary.nearAlertCount > 0 ? "amber" : "success");

        for (int i = 0; i < ITEM_ROWS.length; i++) {
            applyItem(runtimeValueStore, ITEM_ROWS[i], summary.itemAt(i));
        }
        applySelectedItem(runtimeValueStore, summary);
        for (int i = 0; i < ALERT_ROWS.length; i++) {
            applyAlert(runtimeValueStore, ALERT_ROWS[i], summary.alertAt(i));
        }
        applySelectedAlert(runtimeValueStore, summary);
        runtimeValueStore.put(SCREEN, "布控提醒", "最近提醒", latestAlert(summary));
        runtimeValueStore.putTone(SCREEN, "布控提醒", "最近提醒", summary.outAlertCount > 0 ? "danger" : summary.nearAlertCount > 0 ? "amber" : "muted");
        runtimeValueStore.put(SCREEN, "布控提醒", "邮箱通知", "已开启 " + summary.emailAlertCount + " 条，布控总数 " + summary.alerts.size() + " 条");
        runtimeValueStore.put(SCREEN, "布控提醒", "手动检查", "管理员可触发 " + summary.enabledAlertCount + " 条启用提醒复查");
    }

    public String statusMessage(ApiResult result, WatchSummary summary) {
        if (result.success) {
            return "观察风控已同步：分组 " + summary.groupNames.size()
                    + " 个，标的 " + summary.items.size()
                    + " 个，提醒 " + summary.alerts.size() + " 条。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用观察池和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private void applyItem(RuntimeValueStore runtimeValueStore, String row, WatchItem item) {
        if (item == null) {
            return;
        }
        runtimeValueStore.putLabel(SCREEN, "持仓列表", row, item.name);
        runtimeValueStore.put(SCREEN, "持仓列表", row, itemValue(item));
        runtimeValueStore.putTone(SCREEN, "持仓列表", row, changeTone(item.changePercent));
    }

    private void applyAlert(RuntimeValueStore runtimeValueStore, String row, WatchAlert alert) {
        if (alert == null) {
            return;
        }
        runtimeValueStore.putLabel(SCREEN, "布控提醒", row, alert.name);
        runtimeValueStore.put(SCREEN, "布控提醒", row, alertValue(alert));
        runtimeValueStore.putTone(SCREEN, "布控提醒", row, alertTone(alert));
    }

    private void applySelectedItem(RuntimeValueStore runtimeValueStore, WatchSummary summary) {
        WatchItem item = summary.itemAt(0);
        if (item == null) {
            return;
        }
        if (!summary.groupIds.isEmpty()) {
            runtimeValueStore.put(SCREEN, "分组ID", summary.groupIds.get(0));
        }
        runtimeValueStore.put(SCREEN, "标的类型", targetTypeLabel(item.type));
        runtimeValueStore.put(SCREEN, "搜索选择", item.name + (item.code.isEmpty() ? "" : " " + item.code));
        runtimeValueStore.put(SCREEN, "买入价", RuntimeFormatters.price(item.buyPrice));
        runtimeValueStore.put(SCREEN, "持仓数量", quantity(item.position));
        if (!summary.groupNames.isEmpty()) {
            runtimeValueStore.put(SCREEN, "所属分组", summary.groupNames.get(0));
        }
        runtimeValueStore.put(SCREEN, "备注", item.remark.isEmpty() ? "未填写备注" : item.remark);
    }

    private void applySelectedAlert(RuntimeValueStore runtimeValueStore, WatchSummary summary) {
        WatchAlert alert = summary.alertAt(0);
        if (alert == null) {
            return;
        }
        runtimeValueStore.put(SCREEN, "提醒类型", targetTypeLabel(alert.type));
        runtimeValueStore.put(SCREEN, "提醒标的", alert.name + (alert.code.isEmpty() ? "" : " " + alert.code));
        runtimeValueStore.put(SCREEN, "阈值编辑", RuntimeFormatters.percent(alert.thresholdPercent));
        runtimeValueStore.put(SCREEN, "启用状态", alert.enabled ? "已开启提醒" : "未开启提醒");
        runtimeValueStore.put(SCREEN, "邮箱通知", alert.emailNotification ? "已开启邮箱提醒" : "未开启邮箱提醒");
    }

    private String itemValue(WatchItem item) {
        String value = RuntimeFormatters.price(item.latestPrice) + "  " + RuntimeFormatters.percent(item.changePercent);
        if (item.hasPositionCost()) {
            value = value + "  浮盈 " + signedMoney(item.profit());
        } else if (item.position > 0) {
            value = value + "  持仓 " + RuntimeFormatters.price(item.position);
        }
        return value;
    }

    private String alertValue(WatchAlert alert) {
        String status = alert.outOfThreshold ? "已触发" : alert.nearThreshold() ? "接近触发" : "未触发";
        return "阈值 " + RuntimeFormatters.percent(alert.thresholdPercent)
                + "，现涨跌 " + RuntimeFormatters.percent(alert.changePercent)
                + "，" + status;
    }

    private String latestAlert(WatchSummary summary) {
        if (summary.latestAlertTime.isEmpty()) {
            return "暂无已发送提醒，当前启用 " + summary.enabledAlertCount + " 条";
        }
        return compactTime(summary.latestAlertTime) + " 已发送最近一次提醒";
    }

    private String alertTone(WatchAlert alert) {
        if (alert.outOfThreshold) {
            return "danger";
        }
        if (alert.nearThreshold()) {
            return "amber";
        }
        return alert.enabled ? "success" : "muted";
    }

    private String changeTone(double changePercent) {
        if (changePercent > 0) {
            return "up";
        }
        if (changePercent < 0) {
            return "down";
        }
        return "muted";
    }

    private String signedMoney(double value) {
        return String.format(Locale.CHINA, "%+.2f", value);
    }

    private String quantity(double value) {
        if (value <= 0) {
            return "--";
        }
        if (Math.floor(value) == value) {
            return String.format(Locale.CHINA, "%.0f", value);
        }
        return String.format(Locale.CHINA, "%.2f", value);
    }

    private String targetTypeLabel(String value) {
        if ("BOND".equalsIgnoreCase(value)) {
            return "可转债";
        }
        if ("INDEX".equalsIgnoreCase(value)) {
            return "指数";
        }
        if ("FUND".equalsIgnoreCase(value)) {
            return "基金";
        }
        if ("SECTOR".equalsIgnoreCase(value)) {
            return "板块";
        }
        return "股票";
    }

    private String compactTime(String value) {
        if (value.length() >= 16) {
            return value.substring(11, 16);
        }
        return value;
    }
}
