package com.scrapider.finance.androidapp.knowledge;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class KnowledgeScreenBinder {
    public void applyMaterials(RuntimeValueStore store, KnowledgeTaskSummary summary) {
        String screen = "知识库材料";
        store.put(screen, "模式与配置", "任务状态", "待提交检索任务");
        store.putTone(screen, "模式与配置", "任务状态", "amber");
        store.put(screen, "任务状态", "轮询状态", "材料检索需先提交任务，当前仅同步入库任务 " + total(summary) + " 个");
        store.put(screen, "任务状态", "失败原因", summary.failedCount > 0 ? "入库任务失败 " + summary.failedCount + " 个，需进入知识入库处理" : "暂无失败任务");
        store.putTone(screen, "任务状态", "失败原因", summary.failedCount > 0 ? "danger" : "success");
    }

    public void applyManager(RuntimeValueStore store, KnowledgeTaskSummary summary) {
        String screen = "知识库管理";
        store.put(screen, "概览指标", "文档数", String.valueOf(total(summary)));
        store.put(screen, "概览指标", "知识条目", String.valueOf(summary.segmentCount));
        store.put(screen, "概览指标", "总文本量", "以片段数 " + summary.segmentCount + " 估算");
        store.put(screen, "概览指标", "最近更新", summary.updatedAt);
        store.put(screen, "选中片段", "元数据", latestLine(summary));
        store.put(screen, "选中片段", "原文", summary.latestFilename.isEmpty() ? "暂无已同步片段" : "来自 " + summary.latestFilename);
    }

    public void applyImport(RuntimeValueStore store, KnowledgeTaskSummary summary) {
        String screen = "知识入库";
        store.put(screen, "任务统计", "处理中", String.valueOf(summary.processingCount));
        store.put(screen, "任务统计", "需复核", String.valueOf(summary.reviewCount));
        store.put(screen, "任务统计", "已完成", String.valueOf(summary.completedCount));
        store.put(screen, "任务统计", "失败", String.valueOf(summary.failedCount));
        store.putTone(screen, "任务统计", "失败", summary.failedCount > 0 ? "danger" : "success");
        store.put(screen, "六阶段流程", "文字识别", progress(summary));
        store.put(screen, "六阶段流程", "清洗", progress(summary));
        store.put(screen, "六阶段流程", "质量校验", progress(summary));
        store.put(screen, "失败处理", "失败原因", summary.failedCount > 0 ? "最近任务存在失败，请查看后台原因" : "暂无失败任务");
        store.putTone(screen, "失败处理", "失败原因", summary.failedCount > 0 ? "danger" : "success");
        store.put(screen, "人工复核", "任务号", summary.latestTaskNo.isEmpty() ? "暂无任务" : summary.latestTaskNo);
        store.put(screen, "人工复核", "段落", latestLine(summary));
    }

    public String statusMessage(ApiResult result, KnowledgeTaskSummary summary, String title) {
        if (result.success) {
            return title + "已同步：文字识别 " + summary.ocrTotal
                    + " 个，手动知识 " + summary.manualTotal
                    + " 个，失败 " + summary.failedCount + " 个。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用知识库任务和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private long total(KnowledgeTaskSummary summary) {
        return summary.ocrTotal + summary.manualTotal;
    }

    private String latestLine(KnowledgeTaskSummary summary) {
        if (summary.latestTaskNo.isEmpty()) {
            return "暂无后端任务记录";
        }
        return summary.latestTaskNo + "，阶段 " + blank(summary.latestStage) + "，进度 " + summary.latestProgress + "%";
    }

    private String progress(KnowledgeTaskSummary summary) {
        if (summary.latestProgress <= 0) {
            return "0%";
        }
        return Math.min(100, summary.latestProgress) + "%";
    }

    private String blank(String value) {
        return value == null || value.isEmpty() ? "未返回" : value;
    }
}
