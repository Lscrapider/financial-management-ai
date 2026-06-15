package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionNavRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class MarketScreenData {
    private MarketScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(new ScreenSpec(
                "20c9a525ac4c470abfc7d3c19c72075f",
                "行情中心",
                "行情",
                "行情查询与详情",
                "统一呈现股票、指数和可转债，保留主从关系和走势切换。",
                false,
                blocks(
                        block("行情筛选", "chips",
                                row("股票", "已选", "blue"),
                                row("指数", "可切换", "muted"),
                                row("可转债", "可切换", "muted"),
                                row("沪深", "市场筛选", "blue"),
                                row("涨跌幅排序", "降序", "amber"),
                                row("更新时间", "09:30", "muted")),
                        block("搜索与列表", "quotes",
                                row("搜索", "输入名称、代码或拼音", "field"),
                                row("贵州茅台 600519", "1528.20  +1.26%", "up"),
                                row("招商银行 600036", "34.82  -0.78%", "down"),
                                row("宁转债 123456", "118.32  溢价率 18.7%", "amber")),
                        block("选中标的详情", "metrics",
                                row("最新价", "1528.20", "danger"),
                                row("涨跌幅", "+1.26%", "danger"),
                                row("成交量", "4.21 万手", "blue"),
                                row("成交额", "42.8 亿", "blue")),
                        block("走势预览", "chart",
                                row("分时", "已选", "blue"),
                                row("日线", "可切换", "muted"),
                                row("周线", "可切换", "muted"),
                                row("月线", "可切换", "muted"),
                                row("范围", "近一年 / 三年 / 全部", "amber"),
                                row("复权", "仅日线、周线、月线显示", "muted")),
                        block("关键字段", "list",
                                row("盘口", "今开 1510.00，最高 1536.80，最低 1502.10", "muted"),
                                actionNavRow("指数跳转", "沪深三百卡片可定位到指数页", "blue", "行情中心"),
                                row("指数范围", "分时、日线、周线、月线均可切换", "muted"),
                                row("可转债字段", "评级 双 A，转股溢价率 18.7%", "amber"),
                                row("转债范围", "支持分时、日线、周线、月线范围选择", "muted"),
                                actionNavRow("更多行情", "进入详情或底部抽屉", "blue", "行情中心"))
                )
        ));
    }
}
