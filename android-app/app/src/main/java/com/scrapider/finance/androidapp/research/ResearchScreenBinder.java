package com.scrapider.finance.androidapp.research;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class ResearchScreenBinder {
    private static final String SCREEN = "报告研究";
    private static final String[] TARGET_ROWS = {"贵州茅台", "中证红利", "宁转债"};

    public void apply(RuntimeValueStore runtimeValueStore, ResearchSummary summary) {
        runtimeValueStore.put(SCREEN, "报告列表筛选", "全部类型", summary.total + " 个标的");
        runtimeValueStore.put(SCREEN, "报告列表筛选", "股票", summary.stockCount + " 个");
        runtimeValueStore.put(SCREEN, "报告列表筛选", "指数", summary.indexCount + " 个");
        runtimeValueStore.put(SCREEN, "报告列表筛选", "可转债", summary.bondCount + " 个");
        runtimeValueStore.put(SCREEN, "报告列表筛选", "生成中", String.valueOf(summary.runningCount()));
        runtimeValueStore.putTone(SCREEN, "报告列表筛选", "生成中", summary.runningCount() > 0 ? "amber" : "success");

        for (int i = 0; i < TARGET_ROWS.length; i++) {
            applyTarget(runtimeValueStore, TARGET_ROWS[i], summary.targetAt(i));
        }
        runtimeValueStore.put(SCREEN, "任务轮询状态", "待处理", summary.pendingCount + " 项");
        runtimeValueStore.putTone(SCREEN, "任务轮询状态", "待处理", summary.pendingCount > 0 ? "amber" : "muted");
        runtimeValueStore.put(SCREEN, "任务轮询状态", "检索中", summary.retrievingCount + " 项");
        runtimeValueStore.putTone(SCREEN, "任务轮询状态", "检索中", summary.retrievingCount > 0 ? "amber" : "success");
        runtimeValueStore.put(SCREEN, "任务轮询状态", "生成中", summary.generatingCount + " 项");
        runtimeValueStore.putTone(SCREEN, "任务轮询状态", "生成中", summary.generatingCount > 0 ? "amber" : "success");
        runtimeValueStore.put(SCREEN, "任务轮询状态", "成功", summary.successCount + " 项");
        runtimeValueStore.putTone(SCREEN, "任务轮询状态", "成功", "success");
        runtimeValueStore.put(SCREEN, "任务轮询状态", "失败", summary.failedCount + " 项");
        runtimeValueStore.putTone(SCREEN, "任务轮询状态", "失败", summary.failedCount > 0 ? "danger" : "muted");

        runtimeValueStore.put(SCREEN, "操作", "创建报告任务", "已同步 " + summary.targets.size() + " 个标的，可进入详情工作台追踪");
        runtimeValueStore.put(SCREEN, "操作", "历史报告", historyLine(summary));
        runtimeValueStore.put(SCREEN, "操作", "重新生成", regenerateLine(summary));
    }

    public String statusMessage(ApiResult result, ResearchSummary summary) {
        if (result.success) {
            return "报告研究已同步：标的 " + summary.total
                    + " 个，生成中 " + summary.runningCount()
                    + " 项，失败 " + summary.failedCount + " 项。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用报告概览和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private void applyTarget(RuntimeValueStore runtimeValueStore, String row, ResearchReportTarget target) {
        if (target == null) {
            return;
        }
        runtimeValueStore.putLabel(SCREEN, "报告标的", row, target.name);
        runtimeValueStore.put(SCREEN, "报告标的", row, "最新版本 " + version(target)
                + "，历史报告 " + target.reportCount
                + "，" + statusLabel(target.status)
                + timeSuffix(target));
        runtimeValueStore.putTone(SCREEN, "报告标的", row, statusTone(target.status));
    }

    private String historyLine(ResearchSummary summary) {
        ResearchReportTarget target = summary.targetAt(0);
        if (target == null) {
            return "暂无后端历史报告，保留本地设计数据";
        }
        return target.name + " 历史 " + target.reportCount + " 份，最新版本 " + version(target);
    }

    private String regenerateLine(ResearchSummary summary) {
        ResearchReportTarget target = summary.targetAt(0);
        if (target == null || target.latestTaskNo.isEmpty()) {
            return "需要先选择已有任务";
        }
        return "基于 " + target.name + " 任务 " + target.latestTaskNo + " 重新提交";
    }

    private String version(ResearchReportTarget target) {
        return target.versionNo > 0 ? String.valueOf(target.versionNo) : "--";
    }

    private String timeSuffix(ResearchReportTarget target) {
        String time = target.displayTime();
        return time.isEmpty() ? "" : "，" + time;
    }

    private String statusLabel(String status) {
        if ("success".equals(status)) {
            return "摘要已生成";
        }
        if ("failed".equals(status)) {
            return "生成失败";
        }
        if ("retrieving_knowledge".equals(status)) {
            return "知识召回中";
        }
        if ("current_scenes_ready".equals(status)) {
            return "场景已计算";
        }
        if ("pending".equals(status)) {
            return "待处理";
        }
        if ("processing_current_scenes".equals(status)) {
            return "场景计算中";
        }
        if ("generating_report".equals(status)) {
            return "报告生成中";
        }
        return "暂无状态";
    }

    private String statusTone(String status) {
        if ("success".equals(status)) {
            return "success";
        }
        if ("failed".equals(status)) {
            return "danger";
        }
        if ("pending".equals(status)
                || "processing_current_scenes".equals(status)
                || "current_scenes_ready".equals(status)
                || "retrieving_knowledge".equals(status)
                || "generating_report".equals(status)) {
            return "amber";
        }
        return "muted";
    }
}
