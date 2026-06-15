package com.scrapider.finance.androidapp.profile;

import com.scrapider.finance.androidapp.network.ApiResult;
import com.scrapider.finance.androidapp.runtime.RuntimeValueStore;

public final class PsychProfileScreenBinder {
    private static final String SCREEN = "投资心理画像";

    public void apply(RuntimeValueStore runtimeValueStore, PsychProfileSummary summary) {
        int total = summary.questions.size();
        int completed = summary.completedCount();
        runtimeValueStore.put(SCREEN, "问卷进度", "已完成", percent(completed, total));
        runtimeValueStore.putTone(SCREEN, "问卷进度", "已完成", summary.completed ? "success" : "blue");
        runtimeValueStore.put(SCREEN, "问卷进度", "剩余题目", percent(Math.max(total - completed, 0), total));
        runtimeValueStore.putTone(SCREEN, "问卷进度", "剩余题目", summary.completed ? "success" : "amber");

        PsychQuestion question = summary.currentQuestion();
        if (question != null) {
            runtimeValueStore.putLabel(SCREEN, "当前问题", "题目 7", question.code);
            runtimeValueStore.put(SCREEN, "当前问题", "题目 7", question.title);
            runtimeValueStore.put(SCREEN, "当前问题", "选项一", question.optionLabel(0));
            runtimeValueStore.put(SCREEN, "当前问题", "选项二", question.optionLabel(1));
            runtimeValueStore.put(SCREEN, "当前问题", "选项三", question.optionLabel(2));
        }

        runtimeValueStore.put(SCREEN, "画像结果", "建议强度", label("adviceStyle", summary.adviceStyle));
        runtimeValueStore.put(SCREEN, "画像结果", "波动情绪", label("riskEmotion", summary.riskEmotion));
        runtimeValueStore.put(SCREEN, "画像结果", "决策风格", label("decisionStyle", summary.decisionStyle));
        runtimeValueStore.put(SCREEN, "画像结果", "操作节奏", label("tradingTempo", summary.tradingTempo));
        runtimeValueStore.put(SCREEN, "画像结果", "回答偏好", label("explanationPreference", summary.explanationPreference));
        runtimeValueStore.put(SCREEN, "画像结果", "持仓心态", mindsetLine(summary));
        runtimeValueStore.put(SCREEN, "画像结果", "画像版本", summary.version > 0 ? "版本 " + summary.version : "未保存");
        runtimeValueStore.putTone(SCREEN, "画像结果", "画像版本", summary.completed ? "blue" : "amber");

        runtimeValueStore.put(SCREEN, "十题概览", "题目一至三", overviewLine(summary, 0, 3));
        runtimeValueStore.put(SCREEN, "十题概览", "题目四至六", overviewLine(summary, 3, 6));
        runtimeValueStore.put(SCREEN, "十题概览", "题目七", question == null ? "等待问卷加载" : question.title);
        runtimeValueStore.put(SCREEN, "十题概览", "题目八至十", overviewLine(summary, 7, 10));

        runtimeValueStore.put(SCREEN, "操作", "保存画像", summary.completed ? "可更新画像" : "需完成 " + total + " 题");
        runtimeValueStore.putTone(SCREEN, "操作", "保存画像", summary.completed ? "blue" : "disabled");
        runtimeValueStore.put(SCREEN, "操作", "影响说明", summary.summary.isEmpty() ? "会影响智能研究助手建议口径" : summary.summary);
    }

    public String statusMessage(ApiResult result, PsychProfileSummary summary) {
        if (result.success) {
            return "投资心理画像已同步：问卷 " + summary.questions.size()
                    + " 题，画像" + (summary.completed ? "已完成" : "未完成") + "。";
        }
        if (result.backendReachable()) {
            return result.message + " 已保留可用问卷和本地设计数据。";
        }
        return result.message + " 页面仍显示本地设计数据。";
    }

    private String percent(int value, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(value * 100f / total) + "%";
    }

    private String overviewLine(PsychProfileSummary summary, int from, int to) {
        if (summary.questions.isEmpty()) {
            return "等待问卷加载";
        }
        int end = Math.min(to, summary.questions.size());
        return summary.completed ? "已完成 " + (end - from) + " 题" : "待完成 " + Math.max(end - from, 0) + " 题";
    }

    private String mindsetLine(PsychProfileSummary summary) {
        if (summary.holdingMindset.isEmpty()) {
            return "待画像生成";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < summary.holdingMindset.size() && i < 2; i++) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(label("holdingMindset", summary.holdingMindset.get(i)));
        }
        return builder.toString();
    }

    private String label(String group, String code) {
        if (code == null || code.isEmpty()) {
            return "待画像生成";
        }
        if ("riskEmotion".equals(group)) {
            if ("average_down_impulse".equals(code)) return "亏损后有补仓冲动";
            if ("can_accept_volatility".equals(code)) return "能接受波动";
            if ("high_volatility_seeking".equals(code)) return "偏好高弹性";
            if ("loss_anxiety".equals(code)) return "亏损焦虑";
            if ("volatility_averse".equals(code)) return "波动回避";
        }
        if ("decisionStyle".equals(group)) {
            if ("clear_conclusion".equals(code)) return "喜欢明确结论";
            if ("clear_conclusion_with_conditions".equals(code)) return "结论 + 条件触发";
            if ("condition_trigger".equals(code)) return "条件触发";
            if ("data_driven".equals(code)) return "数据逻辑";
            if ("risk_first".equals(code)) return "风险优先";
        }
        if ("explanationPreference".equals(group)) {
            if ("conditional_branches".equals(code)) return "条件分支";
            if ("conclusion_with_key_reason".equals(code)) return "结论 + 关键理由";
            if ("full_data_logic".equals(code)) return "完整数据逻辑";
            if ("short_conclusion".equals(code)) return "简短结论";
        }
        if ("holdingMindset".equals(group)) {
            if ("chase_high_tendency".equals(code)) return "容易追高";
            if ("decision_hesitation".equals(code)) return "压力下犹豫";
            if ("entry_patience".equals(code)) return "有等待耐心";
            if ("hard_to_stop_loss".equals(code)) return "止损容易犹豫";
            if ("plan_based".equals(code)) return "能按计划执行";
            if ("profit_taking_early".equals(code)) return "盈利容易拿不住";
            if ("stop_loss_discipline".equals(code)) return "止损纪律较好";
            if ("trend_holding_ability".equals(code)) return "能顺势持有";
        }
        if ("tradingTempo".equals(group)) {
            if ("fast_short_term".equals(code)) return "短线快进快出";
            if ("long_term_holding".equals(code)) return "中长期持有";
            if ("market_driven_uncertain".equals(code)) return "容易被行情带着走";
            if ("patient_wait".equals(code)) return "等待确定性";
            if ("swing_holding".equals(code)) return "波段持有";
            if ("trial_position".equals(code)) return "小仓位试错";
        }
        if ("adviceStyle".equals(group)) {
            if ("balanced".equals(code)) return "均衡";
            if ("risk_first".equals(code)) return "风险优先";
            if ("condition_first".equals(code)) return "条件优先";
        }
        return code;
    }
}
