package com.scrapider.finance.ai.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.domain.param.InvestorPsychProfileAnswerParam;
import com.scrapider.finance.ai.domain.param.InvestorPsychProfileSubmitParam;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class InvestorPsychProfileScoringHandler {

    public static final String ADVICE_RISK_FIRST = "risk_first";
    public static final String ADVICE_CONDITIONAL_TRADE = "conditional_trade";
    public static final String ADVICE_EXPLICIT_LIGHT_POSITION = "explicit_trade_light_position";
    public static final String ADVICE_EXPLICIT_WITH_POSITION = "explicit_trade_with_position";

    private static final Set<String> REQUIRED_QUESTION_CODES = Set.of(
            "Q1", "Q2", "Q3", "Q4", "Q5", "Q6", "Q7", "Q8", "Q9", "Q10");

    private static final Map<String, Map<String, Map<String, Integer>>> SCORE_RULES = scoreRules();

    public InvestorPsychProfileVO score(InvestorPsychProfileSubmitParam param) {
        Map<String, String> answers = this.normalizeAnswers(param);
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            Map<String, Integer> optionScores = SCORE_RULES
                    .getOrDefault(entry.getKey(), Map.of())
                    .getOrDefault(entry.getValue(), Map.of());
            optionScores.forEach((tag, score) -> scores.merge(tag, score, Integer::sum));
        }

        String riskEmotion = this.riskEmotion(scores);
        String decisionStyle = this.decisionStyle(scores);
        List<String> holdingMindset = this.holdingMindset(scores);
        String tradingTempo = this.tradingTempo(scores);
        String explanationPreference = this.explanationPreference(scores);
        String rawAdviceStyle = this.rawAdviceStyle(scores);
        String adviceStyle = ADVICE_EXPLICIT_WITH_POSITION.equals(rawAdviceStyle)
                ? ADVICE_EXPLICIT_LIGHT_POSITION
                : rawAdviceStyle;

        return new InvestorPsychProfileVO(
                true,
                null,
                riskEmotion,
                decisionStyle,
                holdingMindset,
                tradingTempo,
                explanationPreference,
                adviceStyle,
                rawAdviceStyle,
                Map.copyOf(scores),
                this.summary(adviceStyle, holdingMindset, decisionStyle));
    }

    private Map<String, String> normalizeAnswers(InvestorPsychProfileSubmitParam param) {
        if (param == null || CollUtil.isEmpty(param.answers())) {
            throw new IllegalArgumentException("投资心理画像问卷答案不能为空");
        }
        Map<String, String> answers = new LinkedHashMap<>();
        for (InvestorPsychProfileAnswerParam answer : param.answers()) {
            if (answer == null || StrUtil.hasBlank(answer.questionCode(), answer.optionCode())) {
                throw new IllegalArgumentException("投资心理画像问卷答案不完整");
            }
            String questionCode = answer.questionCode().trim().toUpperCase();
            String optionCode = answer.optionCode().trim().toUpperCase();
            if (!REQUIRED_QUESTION_CODES.contains(questionCode)) {
                throw new IllegalArgumentException("不支持的投资心理画像题目: " + questionCode);
            }
            if (!SCORE_RULES.getOrDefault(questionCode, Map.of()).containsKey(optionCode)) {
                throw new IllegalArgumentException("不支持的投资心理画像选项: " + questionCode + "." + optionCode);
            }
            answers.put(questionCode, optionCode);
        }
        if (!answers.keySet().containsAll(REQUIRED_QUESTION_CODES)) {
            throw new IllegalArgumentException("投资心理画像问卷必须完成全部 10 题");
        }
        return answers;
    }

    private String riskEmotion(Map<String, Integer> scores) {
        if (score(scores, "loss_anxiety") >= 4) {
            return "loss_anxiety";
        }
        if (score(scores, "volatility_averse") >= 4) {
            return "volatility_averse";
        }
        if (score(scores, "high_volatility_seeking") >= 3) {
            return "high_volatility_seeking";
        }
        if (score(scores, "average_down_tendency") >= 4) {
            return "average_down_impulse";
        }
        return "can_accept_volatility";
    }

    private String decisionStyle(Map<String, Integer> scores) {
        if (score(scores, "clear_conclusion_preference") >= 3
                && score(scores, "condition_trigger_preference") >= 3) {
            return "clear_conclusion_with_conditions";
        }
        return Map.of(
                        "clear_conclusion", score(scores, "clear_conclusion_preference"),
                        "risk_first", score(scores, "risk_first_preference"),
                        "condition_trigger", score(scores, "condition_trigger_preference"),
                        "data_driven", score(scores, "data_driven_preference"))
                .entrySet()
                .stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("condition_trigger");
    }

    private List<String> holdingMindset(Map<String, Integer> scores) {
        List<String> mindset = new ArrayList<>();
        if (score(scores, "chase_high_tendency") >= 3 || score(scores, "fomo_level") >= 3) {
            mindset.add("chase_high_tendency");
        }
        if (score(scores, "entry_patience") >= 3) {
            mindset.add("entry_patience");
        }
        if (score(scores, "hard_to_stop_loss") >= 3) {
            mindset.add("hard_to_stop_loss");
        }
        if (score(scores, "stop_loss_discipline") >= 4) {
            mindset.add("stop_loss_discipline");
        }
        if (score(scores, "profit_taking_early") >= 3) {
            mindset.add("profit_taking_early");
        }
        if (score(scores, "trend_holding_ability") >= 3) {
            mindset.add("trend_holding_ability");
        }
        if (score(scores, "decision_hesitation") >= 4) {
            mindset.add("decision_hesitation");
        }
        if (score(scores, "plan_based") >= 3) {
            mindset.add("plan_based");
        }
        return mindset;
    }

    private String tradingTempo(Map<String, Integer> scores) {
        if (score(scores, "fast_trade_preference") >= 3) {
            return "fast_short_term";
        }
        if (score(scores, "trial_position_preference") >= 3) {
            return "trial_position";
        }
        if (score(scores, "long_term_preference") >= 3) {
            return "long_term_holding";
        }
        if (score(scores, "patient_wait_preference") >= 3 || score(scores, "entry_patience") >= 3) {
            return "patient_wait";
        }
        if (score(scores, "decision_hesitation") >= 4) {
            return "market_driven_uncertain";
        }
        return "swing_holding";
    }

    private String explanationPreference(Map<String, Integer> scores) {
        if (score(scores, "short_answer_preference") >= 3) {
            return "short_conclusion";
        }
        if (score(scores, "condition_trigger_preference") >= 3) {
            return "conditional_branches";
        }
        if (score(scores, "full_logic_preference") >= 3 || score(scores, "data_driven_preference") >= 4) {
            return "full_data_logic";
        }
        return "conclusion_with_key_reason";
    }

    private String rawAdviceStyle(Map<String, Integer> scores) {
        if (score(scores, "loss_anxiety") >= 4
                || score(scores, "hard_to_stop_loss") >= 4
                || score(scores, "decision_hesitation") >= 4) {
            return ADVICE_RISK_FIRST;
        }
        if (score(scores, "chase_high_tendency") >= 3
                || score(scores, "average_down_tendency") >= 4
                || score(scores, "execution_discipline") <= 1) {
            return ADVICE_CONDITIONAL_TRADE;
        }
        if (score(scores, "stop_loss_discipline") >= 4
                && score(scores, "execution_discipline") >= 3
                && score(scores, "clear_conclusion_preference") >= 3
                && score(scores, "chase_high_tendency") < 3
                && score(scores, "hard_to_stop_loss") < 3) {
            return ADVICE_EXPLICIT_LIGHT_POSITION;
        }
        if (score(scores, "stop_loss_discipline") >= 5
                && score(scores, "execution_discipline") >= 4
                && score(scores, "data_driven_preference") >= 3
                && score(scores, "chase_high_tendency") < 2
                && score(scores, "average_down_tendency") < 3) {
            return ADVICE_EXPLICIT_WITH_POSITION;
        }
        return ADVICE_CONDITIONAL_TRADE;
    }

    private String summary(String adviceStyle, List<String> holdingMindset, String decisionStyle) {
        List<String> lines = new ArrayList<>();
        if (decisionStyle.contains("clear_conclusion")) {
            lines.add("你偏好明确结论，AI 会优先给出结论再解释依据。");
        } else if ("risk_first".equals(decisionStyle)) {
            lines.add("你更重视风险，AI 会先说明不适合参与的情况。");
        } else if ("condition_trigger".equals(decisionStyle)) {
            lines.add("你适合条件触发型建议，AI 会用“如果怎样就怎样”的结构回答。");
        }
        if (holdingMindset.contains("chase_high_tendency")) {
            lines.add("你有一定追高倾向，AI 会避免给追入式建议，并强调等待确认。");
        }
        if (holdingMindset.contains("hard_to_stop_loss")) {
            lines.add("你在止损上可能会犹豫，AI 会把退出条件放在建议主体里。");
        }
        if (ADVICE_EXPLICIT_LIGHT_POSITION.equals(adviceStyle)) {
            lines.add("你适合明确买卖建议，但仓位会以轻仓试错为主，并搭配止损条件。");
        } else if (ADVICE_CONDITIONAL_TRADE.equals(adviceStyle)) {
            lines.add("你更适合条件建议，AI 会先给触发条件，再讨论是否行动。");
        } else {
            lines.add("你更适合先看风险和观察条件，AI 不会直接给重仓买入建议。");
        }
        return String.join("\n", lines);
    }

    private static int score(Map<String, Integer> scores, String tag) {
        return scores.getOrDefault(tag, 0);
    }

    private static Map<String, Map<String, Map<String, Integer>>> scoreRules() {
        Map<String, Map<String, Map<String, Integer>>> rules = new LinkedHashMap<>();
        put(rules, "Q1", "A", Map.of("loss_anxiety", 2, "volatility_averse", 1));
        put(rules, "Q1", "B", Map.of("can_accept_volatility", 2, "holding_stability", 1));
        put(rules, "Q1", "C", Map.of("average_down_tendency", 2, "hard_to_stop_loss", 1));
        put(rules, "Q1", "D", Map.of("stop_loss_discipline", 2, "plan_based", 1));
        put(rules, "Q2", "A", Map.of("entry_patience", 2, "chase_high_tendency", -1));
        put(rules, "Q2", "B", Map.of("trial_position_preference", 2, "can_accept_volatility", 1));
        put(rules, "Q2", "C", Map.of("fomo_level", 2, "chase_high_tendency", 2));
        put(rules, "Q2", "D", Map.of("volatility_averse", 1, "loss_anxiety", 1));
        put(rules, "Q3", "A", Map.of("stop_loss_discipline", 3, "execution_discipline", 1));
        put(rules, "Q3", "B", Map.of("hard_to_stop_loss", 2, "decision_hesitation", 1));
        put(rules, "Q3", "C", Map.of("average_down_tendency", 3, "hard_to_stop_loss", 1));
        put(rules, "Q3", "D", Map.of("decision_hesitation", 2, "plan_dependence", 1));
        put(rules, "Q4", "A", Map.of("profit_taking_early", 2, "loss_anxiety", 1));
        put(rules, "Q4", "B", Map.of("partial_position_comfort", 2, "plan_based", 1));
        put(rules, "Q4", "C", Map.of("trend_holding_ability", 3, "can_accept_volatility", 1));
        put(rules, "Q4", "D", Map.of("profit_taking_early", 1, "decision_hesitation", 2));
        put(rules, "Q5", "A", Map.of("clear_conclusion_preference", 3, "short_answer_preference", 1));
        put(rules, "Q5", "B", Map.of("risk_first_preference", 3));
        put(rules, "Q5", "C", Map.of("condition_trigger_preference", 3, "plan_based", 1));
        put(rules, "Q5", "D", Map.of("data_driven_preference", 3, "full_logic_preference", 1));
        put(rules, "Q6", "A", Map.of("execution_discipline", 3, "stop_loss_discipline", 2));
        put(rules, "Q6", "B", Map.of("execution_discipline", 1, "decision_hesitation", 1));
        put(rules, "Q6", "C", Map.of("hard_to_stop_loss", 3, "execution_discipline", -1));
        put(rules, "Q6", "D", Map.of("plan_dependence", 2, "decision_hesitation", 1));
        put(rules, "Q7", "A", Map.of("entry_patience", 2, "patient_wait_preference", 2));
        put(rules, "Q7", "B", Map.of("trial_position_preference", 3, "plan_based", 1));
        put(rules, "Q7", "C", Map.of("fast_trade_preference", 3, "chase_high_tendency", 1));
        put(rules, "Q7", "D", Map.of("long_term_preference", 3));
        put(rules, "Q8", "A", Map.of("volatility_averse", 3, "loss_anxiety", 1));
        put(rules, "Q8", "B", Map.of("trial_position_preference", 2, "can_accept_volatility", 1));
        put(rules, "Q8", "C", Map.of("high_volatility_seeking", 3, "fast_trade_preference", 1));
        put(rules, "Q8", "D", Map.of("risk_first_preference", 2, "plan_based", 1));
        put(rules, "Q9", "A", Map.of("loss_anxiety", 2, "volatility_averse", 1));
        put(rules, "Q9", "B", Map.of("plan_based", 2, "can_accept_volatility", 1));
        put(rules, "Q9", "C", Map.of("average_down_tendency", 2, "high_volatility_seeking", 1));
        put(rules, "Q9", "D", Map.of("decision_hesitation", 2, "plan_dependence", 1));
        put(rules, "Q10", "A", Map.of("short_answer_preference", 3, "clear_conclusion_preference", 1));
        put(rules, "Q10", "B", Map.of("clear_conclusion_preference", 2, "data_driven_preference", 1));
        put(rules, "Q10", "C", Map.of("condition_trigger_preference", 3));
        put(rules, "Q10", "D", Map.of("full_logic_preference", 3, "data_driven_preference", 2));
        return rules;
    }

    private static void put(
            Map<String, Map<String, Map<String, Integer>>> rules,
            String questionCode,
            String optionCode,
            Map<String, Integer> scores) {
        rules.computeIfAbsent(questionCode, key -> new LinkedHashMap<>()).put(optionCode, scores);
    }
}
