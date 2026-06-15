package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionNavRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.commandRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.navRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class ResearchScreenData {
    private ResearchScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(reportResearch(), reportWorkbench(), psychologyProfile());
    }

    private static ScreenSpec reportResearch() {
        return new ScreenSpec(
                "18d366550f0c421a862fadf617c5bd42",
                "报告研究",
                "研究",
                "标的分析报告",
                "按标的创建、追踪和复用分析报告。",
                false,
                blocks(
                        block("报告列表筛选", "chips",
                                row("全部类型", "已选", "blue"),
                                row("股票", "筛选", "muted"),
                                row("指数", "筛选", "muted"),
                                row("可转债", "筛选", "muted"),
                                row("生成中", "1", "amber")),
                        block("报告标的", "list",
                                row("贵州茅台", "最新版本 3，历史报告 8，摘要已生成", "blue"),
                                row("中证红利", "最新版本 2，引用证据 6 条", "success"),
                                row("宁转债", "检索中，等待报告正文", "amber")),
                        block("新建报告抽屉", "form",
                                row("配置分组", "默认研究模板", "field"),
                                row("标的搜索", "输入名称或代码选择标的", "field"),
                                row("报告类型", "综合分析", "field"),
                                row("召回条数", "12", "field"),
                                row("日线 / 周线 / 月线", "120 / 60 / 24", "field"),
                                row("核心参数", "65%", "slider")),
                        block("任务轮询状态", "steps",
                                row("待处理", "100%", "success"),
                                row("检索中", "100%", "success"),
                                row("生成中", "60%", "amber"),
                                row("成功", "0%", "muted"),
                                row("失败", "0%", "muted")),
                        block("操作", "list",
                                actionNavRow("创建报告任务", "主操作", "blue", "报告详情工作台"),
                                actionRow("保存为配置", "保存当前参数模板", "blue"),
                                row("更新配置", "覆盖当前自定义配置", "amber"),
                                row("删除配置", "删除前确认是否影响后续任务", "danger"),
                                row("历史报告", "版本 3 / 版本 2 / 版本 1", "muted"),
                                commandRow("重新生成", "基于最新报告任务重新提交", "blue", "regenerateReport"),
                                navRow("查看详情", "进入报告详情工作台", "muted", "报告详情工作台"),
                                row("导出文档", "报告完成后导出研究文档", "muted"))
                )
        );
    }

    private static ScreenSpec reportWorkbench() {
        return new ScreenSpec(
                "1b7d03719baf4d5fb03dc52da6f2db1d",
                "报告详情工作台",
                "研究",
                "报告与行情证据并列",
                "用组件堆栈替代桌面拖拽网格。",
                false,
                blocks(
                        block("报告抬头", "metrics",
                                row("标的", "贵州茅台", "blue"),
                                row("版本", "3", "blue"),
                                row("生成时间", "09:12", "muted"),
                                row("引用证据", "8 条", "amber")),
                        block("报告正文", "paragraph",
                                row("趋势判断", "短期价格强于行业均值，但成交额放大需要结合财报证据复核。", "muted"),
                                row("风险提示", "估值修复依赖消费复苏，不能仅凭单日涨幅判断趋势。", "danger"),
                                row("证据标签", "年报片段、行业景气、分红记录", "blue")),
                        block("组件堆栈", "list",
                                row("报告主体", "已固定在顶部", "blue"),
                                row("走势图", "分时与日线可切换", "muted"),
                                row("盘口数据", "最新价、成交量、买卖盘", "muted"),
                                row("详情数据", "估值、分红、财务摘要", "muted")),
                        block("工作台操作", "chips",
                                row("配置组件", "打开", "blue"),
                                row("选择历史报告", "版本 2", "muted"),
                                row("重新生成", "沿用当前报告参数", "blue"),
                                row("导出文档", "导出研究文档", "blue"))
                )
        );
    }

    private static ScreenSpec psychologyProfile() {
        return new ScreenSpec(
                "60b54263b6474d4eadb82481d3a63330",
                "投资心理画像",
                "研究",
                "交易场景问卷",
                "通过十题问卷影响智能研究助手建议口径。",
                false,
                blocks(
                        block("问卷进度", "progress",
                                row("已完成", "70%", "blue"),
                                row("剩余题目", "30%", "amber")),
                        block("当前问题", "list",
                                row("题目 7", "持仓下跌 8% 且基本面未变时，你更倾向如何处理？", "blue"),
                                row("选项一", "继续观察，等待证据变化", "success"),
                                row("选项二", "减仓降低波动压力", "muted"),
                                row("选项三", "补仓拉低成本", "muted")),
                        block("画像结果", "metrics",
                                row("建议强度", "稳健", "success"),
                                row("波动情绪", "中等敏感", "amber"),
                                row("决策风格", "证据优先", "blue"),
                                row("操作节奏", "低频", "success"),
                                row("回答偏好", "证据优先", "blue"),
                                row("持仓心态", "耐心观察", "success"),
                                row("画像版本", "2026-06-15 09:20", "blue")),
                        block("十题概览", "steps",
                                row("题目一至三", "风险承受、止损纪律、仓位压力已完成", "success"),
                                row("题目四至六", "追涨冲动、回撤反应、信息偏好已完成", "success"),
                                row("题目七", "当前正在回答持仓下跌场景", "blue"),
                                row("题目八至十", "交易节奏、复盘习惯、助手口径待完成", "amber")),
                        block("操作", "chips",
                                row("重置答案", "次级", "muted"),
                                row("保存画像", "需完成 10 题", "disabled"),
                                row("影响说明", "会影响智能研究助手建议口径", "amber"))
                )
        );
    }
}
