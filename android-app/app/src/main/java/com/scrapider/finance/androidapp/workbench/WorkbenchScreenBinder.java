package com.scrapider.finance.androidapp.workbench;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class WorkbenchScreenBinder {
    private static final String SCREEN = "投资工作台";
    private static final String[] REPORT_ROWS = {"最新报告", "报告二", "报告三", "报告四"};

    public void apply(RuntimeValueStore runtimeValueStore, WorkbenchSummary summary) {
        runtimeValueStore.put(SCREEN, "驾驶舱信号", "关注", summary.focusCount + " 项");
        runtimeValueStore.putTone(SCREEN, "驾驶舱信号", "关注", summary.focusCount > 0 ? "amber" : "success");
        runtimeValueStore.put(SCREEN, "驾驶舱信号", "观察池", String.valueOf(summary.watchItemCount));
        runtimeValueStore.put(SCREEN, "驾驶舱信号", "越界", String.valueOf(summary.outAlertCount));
        runtimeValueStore.putTone(SCREEN, "驾驶舱信号", "越界", summary.outAlertCount > 0 ? "danger" : "success");
        runtimeValueStore.put(SCREEN, "驾驶舱信号", "生成中", String.valueOf(summary.reportGeneratingCount));
        runtimeValueStore.putTone(SCREEN, "驾驶舱信号", "生成中", summary.reportGeneratingCount > 0 ? "amber" : "muted");
        runtimeValueStore.put(SCREEN, "驾驶舱信号", "更新时间", summary.updatedAt);

        runtimeValueStore.put(SCREEN, "今日行动", "风险处理", summary.primaryAction);
        runtimeValueStore.putTone(SCREEN, "今日行动", "风险处理", summary.outAlertCount > 0 ? "danger" : summary.nearAlertCount > 0 ? "amber" : "blue");
        runtimeValueStore.put(SCREEN, "今日行动", "报告跟进", summary.reportAction);
        runtimeValueStore.putTone(SCREEN, "今日行动", "报告跟进", summary.reportFailedCount > 0 ? "danger" : summary.reportGeneratingCount > 0 ? "amber" : "blue");
        runtimeValueStore.put(SCREEN, "今日行动", "行情复盘", summary.marketAction);
        runtimeValueStore.putTone(SCREEN, "今日行动", "行情复盘", summary.watchUpCount + summary.watchDownCount > 0 ? "amber" : "muted");

        applyMovement(runtimeValueStore, summary, 0, "异动一");
        applyMovement(runtimeValueStore, summary, 1, "异动二");
        applyMovement(runtimeValueStore, summary, 2, "异动三");

        runtimeValueStore.put(SCREEN, "资产分布", "股票", percent(summary.stockItemCount, summary.watchItemCount));
        runtimeValueStore.put(SCREEN, "资产分布", "指数", percent(summary.indexItemCount, summary.watchItemCount));
        runtimeValueStore.put(SCREEN, "资产分布", "可转债", percent(summary.bondItemCount, summary.watchItemCount));

        applyReports(runtimeValueStore, summary);
    }

    public String statusMessage(ApiResult result, WorkbenchSummary summary) {
        if (result.success) {
            return "投资工作台已同步：观察池 " + summary.watchItemCount
                    + " 个标的，越界 " + summary.outAlertCount
                    + " 条，生成中 " + summary.reportGeneratingCount + " 份。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用分区和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private void applyMovement(RuntimeValueStore runtimeValueStore, WorkbenchSummary summary, int index, String rowLabel) {
        if (summary.movements.size() <= index) {
            runtimeValueStore.put(SCREEN, "观察池异动", rowLabel, "暂无更多异动");
            runtimeValueStore.putTone(SCREEN, "观察池异动", rowLabel, "muted");
            return;
        }
        WorkbenchMovement movement = summary.movements.get(index);
        runtimeValueStore.putLabel(SCREEN, "观察池异动", rowLabel, movement.label());
        runtimeValueStore.put(SCREEN, "观察池异动", rowLabel, movement.value());
        runtimeValueStore.putTone(SCREEN, "观察池异动", rowLabel, movement.tone());
    }

    private void applyReports(RuntimeValueStore runtimeValueStore, WorkbenchSummary summary) {
        for (int i = 0; i < REPORT_ROWS.length; i++) {
            String rowLabel = REPORT_ROWS[i];
            if (summary.reportLines.size() <= i) {
                runtimeValueStore.put(SCREEN, "报告动态", rowLabel, i == 0 ? "暂无报告动态" : "暂无更多报告动态");
                runtimeValueStore.putTone(SCREEN, "报告动态", rowLabel, "muted");
                continue;
            }
            WorkbenchReportLine line = summary.reportLines.get(i);
            runtimeValueStore.put(SCREEN, "报告动态", rowLabel, line.text);
            runtimeValueStore.putTone(SCREEN, "报告动态", rowLabel, line.tone);
        }
    }

    private String percent(int count, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(count * 100f / total) + "%";
    }
}
