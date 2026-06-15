package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.ArrayList;
import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.navRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

public final class ScreenBehaviorData {
    private ScreenBehaviorData() {
    }

    public static List<RowSpec> drawerRows(ScreenSpec screen, RowSpec source) {
        List<RowSpec> rows = new ArrayList<>();
        rows.add(row("当前项", source.label + "：" + source.value, source.tone));

        if ("登录与权限".equals(screen.title)) {
            rows.add(row("登录状态", "提交后保存访问令牌，并拉取用户信息和角色", "blue"));
            rows.add(row("滑块校验", "已通过", "success"));
            rows.add(navRow("登录", "进入投资工作台", "nav", "投资工作台"));
            rows.add(row("失败反馈", "账号或密码错误时显示原因，不清空输入", "danger"));
        } else if ("行情中心".equals(screen.title)) {
            rows.add(row("标的详情", "最新价、涨跌幅、成交量、成交额、盘口字段同步更新", "blue"));
            rows.add(row("周期", "分时 / 日线 / 周线 / 月线", "field"));
            rows.add(navRow("更多行情", "打开行情详情抽屉，保留指数跳转和转债评级字段", "primary", "行情中心"));
        } else if ("观察风控".equals(screen.title)) {
            rows.add(row("买入价", "34.10", "field"));
            rows.add(row("持仓数量", "2000", "field"));
            rows.add(row("启用提醒", "已开启", "switch"));
            rows.add(row("涨跌幅阈值", "5%", "slider"));
            rows.add(row("删除标的", "删除前二次确认", "danger"));
        } else if ("报告研究".equals(screen.title)) {
            rows.add(row("配置模板", "默认研究模板", "field"));
            rows.add(row("报告类型", "综合分析", "field"));
            rows.add(row("召回条数", "12", "field"));
            rows.add(row("核心参数", "65%", "slider"));
            rows.add(navRow("创建报告任务", "进入报告详情工作台", "nav", "报告详情工作台"));
        } else if ("报告详情工作台".equals(screen.title)) {
            rows.add(row("历史报告", "版本 2 / 版本 3", "field"));
            rows.add(row("证据引用", "年报片段、行业景气、分红记录", "blue"));
            rows.add(row("组件", "报告主体 / 走势图 / 盘口数据 / 详情数据", "field"));
            rows.add(row("导出文档", "导出文档", "primary"));
        } else if ("投资心理画像".equals(screen.title)) {
            rows.add(row("选项一", "继续观察，等待证据变化", "success"));
            rows.add(row("选项二", "减仓降低波动压力", "field"));
            rows.add(row("选项三", "补仓拉低成本", "field"));
            rows.add(row("保存画像", "完成 10 题后启用", "primary"));
        } else if ("知识库材料".equals(screen.title)) {
            rows.add(row("检索模式", "按标的 / 自然语言", "field"));
            rows.add(row("自然语言问题", "请检索最近两年分红稳定性和估值风险相关材料。", "field"));
            rows.add(row("结果筛选", "场景、标签、知识库名称", "blue"));
            rows.add(navRow("检索材料", "查看知识库片段", "nav", "知识库管理"));
        } else if ("知识库管理".equals(screen.title)) {
            rows.add(row("来源类型", "文字识别 / 手动导入", "field"));
            rows.add(row("编辑原文", "保存后重新向量化", "amber"));
            rows.add(row("编辑标签", "只更新标签，不重新向量化", "blue"));
            rows.add(row("保存变更", "保存变更", "primary"));
        } else if ("知识入库".equals(screen.title)) {
            rows.add(row("任务进度", "80%", "progress"));
            rows.add(row("段落文本", "第 3 段，低置信度提示 2 条", "field"));
            rows.add(row("页面影像证据", "打开原始页图抽屉核对", "blue"));
            rows.add(row("提交复核", "提交复核", "danger"));
        } else if ("系统管理".equals(screen.title)) {
            rows.add(row("日志筛选", "来源 / 阶段 / 模型 / 用户", "field"));
            rows.add(row("同步进度", "70%", "progress"));
            rows.add(row("删除确认", "输入标的代码后才允许物理删除", "field"));
            rows.add(row("物理删除", "物理删除", "danger"));
        } else if ("我的与智能研究助手".equals(screen.title)) {
            rows.add(row("上下文", "当前页、自选池、近 20 条会话、心理画像、知识库材料", "blue"));
            rows.add(row("输入问题", "继续输入研究问题", "field"));
            rows.add(row("流式状态", "正在根据证据生成回答", "progress"));
            rows.add(navRow("发送问题", "发送问题", "primary", "我的与智能研究助手"));
        } else {
            rows.add(row("详情", "打开当前条目的移动端详情抽屉", "blue"));
            rows.add(row("刷新", "刷新当前模块数据", "primary"));
        }
        return rows;
    }

}
