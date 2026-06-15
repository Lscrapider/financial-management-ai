package com.scrapider.finance.androidapp.workbench;

import com.scrapider.finance.androidapp.model.BlockSpec;
import com.scrapider.finance.androidapp.model.RowSpec;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorkbenchPageSpecFactory {
    public static final String SCREEN_TITLE = "投资工作台";

    private WorkbenchPageSpecFactory() {
    }

    public static List<ScreenSpec> create() {
        return Collections.singletonList(new ScreenSpec(
                "9f8ae355b69749faa229e3b137872ad5",
                SCREEN_TITLE,
                "工作台",
                "登录后默认首页",
                "先展示今日行动、风险信号、观察池异动和报告动态。",
                false,
                Arrays.asList(
                        block("驾驶舱信号", "metrics",
                                row("关注", "0 项", "muted"),
                                row("观察池", "0", "blue"),
                                row("越界", "0", "success"),
                                row("生成中", "0", "amber"),
                                row("更新时间", "--:--", "muted")),
                        block("今日行动", "list",
                                row("风险处理", "等待后端同步布控提醒。", "muted"),
                                row("报告跟进", "等待后端同步报告动态。", "muted"),
                                row("行情复盘", "等待后端同步行情异动。", "muted")),
                        block("观察池异动", "quotes",
                                row("异动一", "等待观察池数据", "muted"),
                                row("异动二", "等待观察池数据", "muted"),
                                row("异动三", "等待观察池数据", "muted")),
                        block("资产分布", "progress",
                                row("股票", "0%", "blue"),
                                row("指数", "0%", "success"),
                                row("可转债", "0%", "amber")),
                        block("报告动态", "list",
                                row("最新报告", "暂无报告动态", "muted"),
                                row("报告二", "暂无更多报告动态", "muted"),
                                row("报告三", "暂无更多报告动态", "muted"),
                                row("报告四", "暂无更多报告动态", "muted"))
                )
        ));
    }

    private static BlockSpec block(String title, String type, RowSpec... rows) {
        return new BlockSpec(title, type, Arrays.asList(rows));
    }

    private static RowSpec row(String label, String value, String tone) {
        return new RowSpec(label, value, tone);
    }

}
