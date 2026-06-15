package com.scrapider.finance.androidapp.workbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorkbenchSummary {
    public int focusCount;
    public int watchGroupCount;
    public int watchItemCount;
    public int stockItemCount;
    public int indexItemCount;
    public int bondItemCount;
    public int watchUpCount;
    public int watchDownCount;
    public int alertCount;
    public int enabledAlertCount;
    public int outAlertCount;
    public int nearAlertCount;
    public int reportTotal;
    public int reportGeneratingCount;
    public int reportFailedCount;
    public String primaryAction = "后端暂无高优先级动作，继续查看观察池和提醒。";
    public String reportAction = "报告目标暂无更新，进入报告研究查看历史版本。";
    public String marketAction = "行情异动待刷新，优先处理布控和报告。";
    public String updatedAt = "--:--";
    public final List<WorkbenchMovement> movements = new ArrayList<>();
    public final List<WorkbenchReportLine> reportLines = new ArrayList<>();

    public void finish() {
        movements.sort(Comparator.comparingDouble((WorkbenchMovement item) -> Math.abs(item.changePercent)).reversed());
        focusCount = outAlertCount + nearAlertCount + reportGeneratingCount + reportFailedCount;
        if (outAlertCount > 0) {
            primaryAction = "有 " + outAlertCount + " 条提醒越界，优先检查布控阈值。";
        } else if (nearAlertCount > 0) {
            primaryAction = "有 " + nearAlertCount + " 条提醒接近阈值，盘后复核价格波动。";
        } else if (watchItemCount > 0) {
            primaryAction = "观察池 " + watchItemCount + " 个标的已同步，检查涨跌分布。";
        }
        if (reportGeneratingCount > 0) {
            reportAction = "有 " + reportGeneratingCount + " 份报告生成中，等待轮询结果。";
        } else if (reportFailedCount > 0) {
            reportAction = "有 " + reportFailedCount + " 份报告失败，进入报告研究处理。";
        }
        if (watchUpCount + watchDownCount > 0) {
            marketAction = "上涨 " + watchUpCount + "、下跌 " + watchDownCount + "，复核观察池和布控阈值。";
        }
    }

    public boolean hasAnyData() {
        return watchGroupCount > 0
                || watchItemCount > 0
                || stockItemCount > 0
                || indexItemCount > 0
                || bondItemCount > 0
                || alertCount > 0
                || nearAlertCount > 0
                || reportTotal > 0
                || reportGeneratingCount > 0
                || reportFailedCount > 0;
    }
}
