package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.commandRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class AdminScreenData {
    private AdminScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(new ScreenSpec(
                "6491996e6bc14d2f84232091053fe2f7",
                "系统管理",
                "我的",
                "管理员管理中心",
                "查看系统健康、调用用量、标的配置和同步状态。",
                true,
                false,
                blocks(
                        block("管理标签", "chips",
                                row("系统监控", "已选", "blue"),
                                row("调用用量", "可切换", "muted"),
                                row("标的配置", "可切换", "muted"),
                                row("数据同步", "运行中", "amber")),
                        block("系统监控", "metrics",
                                row("访问量", "218", "blue"),
                                row("用户数", "4", "blue"),
                                row("智能调用", "37", "amber"),
                                row("同步状态", "运行中", "amber")),
                        block("调用用量", "list",
                                row("总费用", "¥18.42", "amber"),
                                row("请求数", "126", "blue"),
                                row("筛选", "来源 / 阶段 / 模型 / 用户", "muted"),
                                actionRow("日志详情", "查看消耗拆分和响应信息", "blue")),
                        block("标的配置", "form",
                                row("新增股票", "输入 6 位代码后提交", "field"),
                                row("新增可转债", "输入 6 位代码后提交", "field"),
                                row("指数新增", "预留入口", "muted"),
                                row("物理删除", "删除行情、报告、提醒、观察池关联数据", "danger")),
                        block("数据同步", "progress",
                                row("股票全量同步", "70%", "amber"),
                                row("指数全量同步", "100%", "success"),
                                row("可转债同步", "30%", "amber"),
                                row("单标的补数", "0%", "muted")),
                        block("同步状态", "list",
                                row("最近任务", "09:22 股票全量同步仍在后台执行", "amber"),
                                row("失败原因", "单标的补数缺少规范化代码时保留原因", "danger"),
                                row("轮询提示", "最多轮询 120 次，超时提示后台执行", "muted"),
                                commandRow("手动刷新", "刷新同步任务状态", "blue", "refresh"))
                )
        ));
    }
}
