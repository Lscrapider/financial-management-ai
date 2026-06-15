package com.scrapider.finance.androidapp.research;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class ReportDetailScreenBinder {
    private static final String SCREEN = "报告详情工作台";

    public void apply(RuntimeValueStore runtimeValueStore, ReportDetailSummary summary) {
        runtimeValueStore.put(SCREEN, "报告抬头", "标的", summary.targetName);
        runtimeValueStore.put(SCREEN, "报告抬头", "版本", version(summary));
        runtimeValueStore.put(SCREEN, "报告抬头", "生成时间", summary.displayTime());
        runtimeValueStore.put(SCREEN, "报告抬头", "引用证据", summary.referenceCount + " 处");
        runtimeValueStore.putTone(SCREEN, "报告抬头", "引用证据", summary.referenceCount > 0 ? "amber" : "muted");

        runtimeValueStore.put(SCREEN, "报告正文", "趋势判断", firstMeaningful(extractSection(summary.reportText, "结论"), extractSection(summary.reportText, "场景解读")));
        runtimeValueStore.put(SCREEN, "报告正文", "风险提示", firstMeaningful(extractSection(summary.reportText, "风险提示"), summary.errorMessage));
        runtimeValueStore.putTone(SCREEN, "报告正文", "风险提示", summary.errorMessage.isEmpty() ? "danger" : "amber");
        runtimeValueStore.put(SCREEN, "报告正文", "证据标签", evidenceLine(summary));

        runtimeValueStore.put(SCREEN, "组件堆栈", "报告主体", statusLabel(summary.status));
        runtimeValueStore.putTone(SCREEN, "组件堆栈", "报告主体", statusTone(summary.status));
        runtimeValueStore.put(SCREEN, "组件堆栈", "走势图", summary.targetCode.isEmpty() ? "等待报告标的" : summary.targetName + " " + summary.targetCode);
        runtimeValueStore.put(SCREEN, "组件堆栈", "盘口数据", summary.reportType.isEmpty() ? "报告类型未返回" : "报告类型 " + summary.reportType);
        runtimeValueStore.put(SCREEN, "组件堆栈", "详情数据", summary.model.isEmpty() ? "模型信息未返回" : "模型 " + summary.model);

        runtimeValueStore.put(SCREEN, "工作台操作", "选择历史报告", "当前版本 " + version(summary));
        runtimeValueStore.put(SCREEN, "工作台操作", "重新生成", summary.taskNo.isEmpty() ? "需要先选择报告任务" : "沿用任务 " + summary.taskNo);
        runtimeValueStore.put(SCREEN, "工作台操作", "导出文档", summary.status.equals("success") ? "可导出研究文档" : "报告完成后可导出");
    }

    public String statusMessage(ApiResult result, ReportDetailSummary summary) {
        if (result.success) {
            if (!summary.hasAnyData()) {
                return "报告详情暂无后端记录，页面仍显示本地设计数据。";
            }
            return "报告详情已同步：" + summary.targetName + "，版本 " + version(summary) + "。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用报告详情和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private String evidenceLine(ReportDetailSummary summary) {
        if (summary.referenceCount > 0) {
            return "报告正文包含 " + summary.referenceCount + " 处知识库引用";
        }
        String marketFacts = extractSection(summary.reportText, "市场事实");
        if (!marketFacts.isEmpty()) {
            return truncate(marketFacts);
        }
        return "暂无引用标签，等待报告生成完成";
    }

    private String firstMeaningful(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return truncate(first);
        }
        if (second != null && !second.trim().isEmpty()) {
            return truncate(second);
        }
        return "暂无正文内容，等待报告生成完成";
    }

    private String extractSection(String text, String title) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String marker = "## " + title;
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = text.indexOf("\n## ", start);
        String section = end < 0 ? text.substring(start) : text.substring(start, end);
        return cleanMarkdown(section);
    }

    private String cleanMarkdown(String value) {
        return value.replace("#", "")
                .replace("*", "")
                .replace("-", "")
                .replace("\n", " ")
                .trim();
    }

    private String truncate(String value) {
        String compact = cleanMarkdown(value);
        if (compact.length() <= 92) {
            return compact;
        }
        return compact.substring(0, 92) + "...";
    }

    private String version(ReportDetailSummary summary) {
        return summary.versionNo > 0 ? String.valueOf(summary.versionNo) : "--";
    }

    private String statusLabel(String status) {
        if ("success".equals(status)) {
            return "报告主体已生成";
        }
        if ("failed".equals(status)) {
            return "报告生成失败";
        }
        if ("generating_report".equals(status)) {
            return "报告正文生成中";
        }
        if ("retrieving_knowledge".equals(status)) {
            return "知识库召回中";
        }
        return status == null || status.isEmpty() ? "暂无报告状态" : "报告处理中";
    }

    private String statusTone(String status) {
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
}
