package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.commandRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class KnowledgeScreenData {
    private KnowledgeScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(knowledgeMaterials(), knowledgeManager(), knowledgeImport());
    }

    private static ScreenSpec knowledgeMaterials() {
        return new ScreenSpec(
                "ebcd6fadff5b455b843d28dc0415dfae",
                "知识库材料",
                "我的",
                "材料检索",
                "检索知识库原文材料，不生成报告。",
                true,
                false,
                blocks(
                        block("模式与配置", "chips",
                                row("按标的", "已选", "blue"),
                                row("自然语言", "可切换", "muted"),
                                row("材料检索", "不是报告生成", "amber"),
                                row("任务状态", "成功", "success"),
                                commandRow("手动刷新", "刷新任务状态", "blue", "refresh")),
                        block("按标的表单", "form",
                                row("配置模板", "默认研究模板", "field"),
                                row("标的类型", "股票", "field"),
                                row("标的搜索", "贵州茅台 600519", "field"),
                                row("报告场景", "估值 / 财报 / 风险", "field"),
                                row("日线 / 周线 / 月线窗口", "120 / 60 / 24", "field"),
                                row("召回条数", "12", "field"),
                                row("参数覆盖", "优先使用本次输入，不改默认模板", "amber")),
                        block("自然语言模式", "paragraph",
                                row("查询输入", "请检索最近两年分红稳定性和估值风险相关材料。", "field"),
                                commandRow("主按钮", "检索材料", "blue", "submitKnowledgeMaterial")),
                        block("结果筛选", "chips",
                                row("筛选场景", "财报", "blue"),
                                row("筛选标签", "分红", "blue"),
                                row("知识库名称", "年报材料", "blue"),
                                row("重置筛选", "恢复全部材料", "muted")),
                        block("任务状态", "list",
                                row("轮询状态", "最近刷新 09:24，已返回 12 条材料", "success"),
                                row("失败原因", "失败时保留任务号和后端原因", "danger"),
                                commandRow("重新检索", "重新提交当前条件", "blue", "submitKnowledgeMaterial")),
                        block("材料结果", "paragraph",
                                row("来源", "贵州茅台 2024 年报，第 18 页，片段 003", "muted"),
                                row("场景标签", "财报分析 / 分红政策", "blue"),
                                row("综合分", "0.86，语义分 0.82", "success"),
                                row("原文", "公司保持较高现金分红比例，但需关注收入增速放缓对估值的影响。", "muted"))
                )
        );
    }

    private static ScreenSpec knowledgeManager() {
        return new ScreenSpec(
                "998598ae7fd84bfc9ad11d63f9a578ec",
                "知识库管理",
                "我的",
                "知识库概览与片段维护",
                "管理员浏览、筛选和维护向量化知识条目。",
                true,
                false,
                blocks(
                        block("概览指标", "metrics",
                                row("文档数", "126", "blue"),
                                row("知识条目", "3852", "blue"),
                                row("总文本量", "92 万字", "muted"),
                                row("最近更新", "09:20", "amber")),
                        block("标签分布", "progress",
                                row("财报分析", "42%", "blue"),
                                row("估值判断", "28%", "success"),
                                row("风险提示", "18%", "danger"),
                                row("行业景气", "12%", "amber")),
                        block("片段浏览器", "form",
                                row("来源类型", "文字识别 / 手动导入", "field"),
                                row("场景", "财报分析", "field"),
                                row("标签", "分红政策", "field"),
                                row("文档名", "贵州茅台 2024 年报", "field")),
                        block("选中片段", "paragraph",
                                row("元数据", "页码 18，段落 4，置信度 0.91，版本 2", "muted"),
                                row("原文", "报告期内现金分红政策保持稳定，覆盖归母净利润较高比例。", "muted"),
                                row("编辑原文", "进入明确编辑态，保存后重新向量化", "amber"),
                                row("编辑标签", "只更新标签，不重新向量化", "blue"))
                )
        );
    }

    private static ScreenSpec knowledgeImport() {
        return new ScreenSpec(
                "7040ac69b43a4748ac85dad1e570b2c5",
                "知识入库",
                "我的",
                "文字识别与手动入库",
                "管理员处理上传、复核、打标和向量入库长任务。",
                true,
                false,
                blocks(
                        block("任务统计", "metrics",
                                row("处理中", "3", "amber"),
                                row("需复核", "1", "danger"),
                                row("已完成", "28", "success"),
                                row("失败", "1", "danger")),
                        block("六阶段流程", "steps",
                                row("文字识别", "100%", "success"),
                                row("清洗", "100%", "success"),
                                row("质量校验", "80%", "amber"),
                                row("场景打标", "40%", "amber"),
                                row("向量入库", "0%", "muted"),
                                row("完成", "0%", "muted")),
                        block("失败处理", "list",
                                row("失败原因", "第 18 页低置信度过多，需人工复核", "danger"),
                                row("后台执行", "关闭页面后继续轮询任务状态", "amber"),
                                actionRow("重试阶段", "从质量校验继续", "blue")),
                        block("人工复核", "paragraph",
                                row("任务号", "识别-20260615-001", "blue"),
                                row("段落", "第 3 段，页码 18，低置信度提示 2 条", "amber"),
                                row("页面影像证据", "右侧预览原始页图，移动端以抽屉查看", "muted"),
                                actionRow("操作", "保存草稿 / 提交复核", "blue")),
                        block("手动知识导入", "form",
                                row("标题", "分红政策补充材料", "field"),
                                row("片段一", "输入原文片段，作为最小入库单元", "field"),
                                row("片段二", "可继续新增多段原文", "field"),
                                row("提交入库", "高风险操作，二次确认", "danger"))
                )
        );
    }
}
