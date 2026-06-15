package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.adminCommandRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.adminRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class WatchScreenData {
    private WatchScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(new ScreenSpec(
                "5a3c4025c7e84dea85871e10eafccf7d",
                "观察风控",
                "观察",
                "观察池与布控提醒",
                "管理分组、持仓成本、备注和阈值提醒。",
                false,
                blocks(
                        block("分组", "chips",
                                row("核心持仓", "18", "blue"),
                                row("低估观察", "9", "muted"),
                                row("转债", "3", "muted"),
                                row("触发提醒", "2", "danger")),
                        block("分组管理", "list",
                                actionRow("新建分组", "创建观察分组", "blue"),
                                row("重命名分组", "修改当前分组名称", "muted"),
                                row("删除分组", "删除前确认是否保留标的", "danger")),
                        block("持仓列表", "quotes",
                                row("宁德时代", "210.50  +2.45%  浮盈 +15500.00", "up"),
                                row("招商银行", "31.20  -1.20%  浮盈 -2400.00", "down"),
                                row("平安转债", "115.50  +0.40%  浮盈 +450.00", "up")),
                        block("添加标的抽屉", "form",
                                row("标的类型", "股票", "field"),
                                row("搜索选择", "宁德时代 300750", "field"),
                                row("买入价", "195.00", "field"),
                                row("持仓数量", "1000", "field"),
                                row("所属分组", "核心持仓", "field"),
                                row("备注", "新能源电池核心", "field"),
                                row("移除标的", "二次确认后从当前分组移除", "danger")),
                        block("布控提醒", "list",
                                row("宁德时代", "接近阈值 · 距设定止盈线 220.00 仅差 4.5%", "amber"),
                                row("招商银行", "阈值 5%，现涨跌 -1.20%，未触发", "success"),
                                row("平安转债", "阈值 3%，现涨跌 +0.40%，已触发", "danger"),
                                row("提醒类型", "股票", "field"),
                                row("提醒标的", "宁德时代 300750", "field"),
                                row("阈值编辑", "5.00%", "field"),
                                row("启用状态", "已开启提醒", "switch"),
                                row("最近提醒", "09:18 已发送接近阈值提醒", "amber"),
                                row("删除提醒", "删除前保留二次确认", "danger"),
                                row("邮箱通知", "已开启邮箱提醒", "switch"),
                                adminCommandRow("手动检查", "管理员可触发布控提醒复查", "blue", "checkAlerts"))
                )
        ));
    }
}
