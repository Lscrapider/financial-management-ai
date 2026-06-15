package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

import static com.scrapider.finance.androidapp.screens.ScreenFactory.actionNavRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.adminActionNavRow;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.block;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.blocks;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.list;
import static com.scrapider.finance.androidapp.screens.ScreenFactory.row;

final class ProfileScreenData {
    private ProfileScreenData() {
    }

    static List<ScreenSpec> create() {
        return list(new ScreenSpec(
                "058ef22426464724b7f07529ccc694c0",
                "我的与智能研究助手",
                "我的",
                "个人中心与全屏助手",
                "管理资料、安全设置、通知和基于证据的研究对话。",
                false,
                blocks(
                        block("个人资料", "form",
                                row("用户名", "研究员01（不可编辑）", "readonly"),
                                row("姓名", "秦泽宇", "field"),
                                row("邮箱", "已绑定", "success"),
                                row("手机", "未绑定", "amber"),
                                row("资料更新时间", "09:25", "readonly")),
                        block("安全与通知", "list",
                                row("修改密码", "需要原密码和确认密码", "amber"),
                                row("邮件通知", "已开启，失败时回滚开关", "switch"),
                                actionNavRow("登录与权限", "切换账号、重新登录或查看暂未接入入口", "blue", "登录与权限")),
                        block("管理中心", "list",
                                adminActionNavRow("管理员入口", "知识库、系统管理、入库任务", "blue", "系统管理"),
                                adminActionNavRow("知识库材料", "进入知识库材料", "blue", "知识库材料"),
                                adminActionNavRow("知识库管理", "进入知识库管理", "blue", "知识库管理"),
                                adminActionNavRow("知识入库", "进入文字识别与手动入库", "blue", "知识入库"),
                                adminActionNavRow("系统管理", "进入系统管理", "blue", "系统管理")),
                        block("智能研究助手", "chips",
                                row("当前页", "上下文", "blue"),
                                row("自选池", "上下文", "blue"),
                                row("近 20 条会话", "上下文", "muted"),
                                row("心理画像", "上下文", "amber"),
                                row("知识库材料", "上下文", "blue")),
                        block("对话", "paragraph",
                                row("模型", "研究助手模型，流式回答中", "amber"),
                                row("用户", "解释贵州茅台最新报告中的主要风险。", "blue"),
                                row("助手", "基于报告引用和知识库材料，主要风险集中在收入增速、估值修复持续性和消费复苏节奏。", "muted"),
                                row("流式状态", "正在追加证据引用和风险解释", "success"),
                                row("输入框", "继续输入研究问题", "field"),
                                row("证据上下文", "当前页、观察池、画像和材料检索结果共同参与", "muted"),
                                row("安全口径", "提供证据和解释，不给出买卖指令", "amber"))
                )
        ));
    }
}
