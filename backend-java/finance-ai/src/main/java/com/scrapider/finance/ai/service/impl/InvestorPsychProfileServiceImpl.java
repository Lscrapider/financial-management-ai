package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.param.InvestorPsychProfileSubmitParam;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileQuestionnaireVO;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileVO;
import com.scrapider.finance.ai.handler.InvestorPsychProfileScoringHandler;
import com.scrapider.finance.ai.service.InvestorPsychProfileService;
import com.scrapider.finance.domain.po.AiInvestorPsychProfilePO;
import com.scrapider.finance.manage.AiInvestorPsychProfileManage;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InvestorPsychProfileServiceImpl implements InvestorPsychProfileService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> SCORE_MAP_TYPE = new TypeReference<>() {
    };

    private final AiInvestorPsychProfileManage profileManage;
    private final InvestorPsychProfileScoringHandler scoringService;
    private final ObjectMapper objectMapper;

    public InvestorPsychProfileServiceImpl(
            AiInvestorPsychProfileManage profileManage,
            InvestorPsychProfileScoringHandler scoringService,
            ObjectMapper objectMapper) {
        this.profileManage = profileManage;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InvestorPsychProfileQuestionnaireVO questionnaire() {
        return new InvestorPsychProfileQuestionnaireVO(List.of(
                question("Q1", "买入后两天亏了 6%，但原本逻辑没明显变，你通常会？",
                        option("A", "很焦虑，想先卖掉"),
                        option("B", "继续观察，不急着处理"),
                        option("C", "想补仓摊低成本"),
                        option("D", "看是否跌破原计划止损位")),
                question("Q2", "一个你关注过的标的连续涨了 20%，但你没买，你更容易？",
                        option("A", "等回调，不追"),
                        option("B", "小仓位试一下"),
                        option("C", "越看越想买，怕错过"),
                        option("D", "放弃，觉得已经晚了")),
                question("Q3", "如果买入理由被破坏，但账面已经亏损，你更可能？",
                        option("A", "按计划止损"),
                        option("B", "再等等，希望反弹"),
                        option("C", "补仓降低成本"),
                        option("D", "不知道怎么处理，会重新找理由")),
                question("Q4", "持仓盈利 12%，但趋势还没明显结束，你通常会？",
                        option("A", "先卖掉，落袋为安"),
                        option("B", "卖一部分，剩下继续看"),
                        option("C", "继续持有，按趋势走"),
                        option("D", "反复纠结，怕利润回吐")),
                question("Q5", "你问 AI “这个能买吗”时，最希望它先给什么？",
                        option("A", "直接结论：买、不买、观望"),
                        option("B", "先告诉我风险"),
                        option("C", "告诉我满足什么条件可以买"),
                        option("D", "先给数据和逻辑依据")),
                question("Q6", "如果 AI 给了“跌破某价格就止损”的计划，你实际执行时更接近？",
                        option("A", "基本能执行"),
                        option("B", "会犹豫，但大多能执行"),
                        option("C", "经常舍不得止损"),
                        option("D", "到时候会再问一遍")),
                question("Q7", "你更舒服的操作方式是？",
                        option("A", "等机会明确后再出手"),
                        option("B", "小仓位先试，再根据走势加减"),
                        option("C", "看到机会就快速参与"),
                        option("D", "更喜欢长期拿着，不频繁处理")),
                question("Q8", "面对一个波动很大的机会，你通常会？",
                        option("A", "不碰，波动太大影响心态"),
                        option("B", "可以小仓位参与"),
                        option("C", "越波动越觉得有机会"),
                        option("D", "先看风险收益比和退出条件")),
                question("Q9", "当市场突然大跌，你更容易？",
                        option("A", "想赶紧降低仓位"),
                        option("B", "观察是否破坏趋势"),
                        option("C", "想找便宜机会加仓"),
                        option("D", "不太知道该怎么判断")),
                question("Q10", "AI 给你建议时，你更能接受哪种表达？",
                        option("A", "简短明确，别太长"),
                        option("B", "结论 + 关键理由"),
                        option("C", "条件分支：如果怎样就怎样"),
                        option("D", "完整分析，数据、逻辑、风险都要有"))));
    }

    @Override
    public InvestorPsychProfileVO current(Long userId) {
        AiInvestorPsychProfilePO profile = this.profileManage.findActive(userId);
        return profile == null ? InvestorPsychProfileVO.empty() : this.toVO(profile);
    }

    @Override
    public InvestorPsychProfileVO submit(Long userId, InvestorPsychProfileSubmitParam param) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        InvestorPsychProfileVO scored = this.scoringService.score(param);
        Long version = this.profileManage.nextVersion(userId);
        AiInvestorPsychProfilePO profile = AiInvestorPsychProfilePO.createActive(
                userId,
                version,
                scored.riskEmotion(),
                scored.decisionStyle(),
                this.toJson(scored.holdingMindset()),
                scored.tradingTempo(),
                scored.explanationPreference(),
                scored.adviceStyle(),
                scored.rawAdviceStyle(),
                this.toJson(scored.tagScores()),
                this.toJson(param.answers()),
                scored.summary());
        this.profileManage.replaceActive(profile);
        return this.toVO(profile);
    }

    private InvestorPsychProfileVO toVO(AiInvestorPsychProfilePO profile) {
        return new InvestorPsychProfileVO(
                true,
                profile.getProfileVersion(),
                profile.getRiskEmotion(),
                profile.getDecisionStyle(),
                this.readJson(profile.getHoldingMindsetJson(), STRING_LIST_TYPE, List.of()),
                profile.getTradingTempo(),
                profile.getExplanationPreference(),
                profile.getAdviceStyle(),
                profile.getRawAdviceStyle(),
                this.readJson(profile.getTagScoresJson(), SCORE_MAP_TYPE, Map.of()),
                profile.getSummary());
    }

    private String toJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("投资心理画像 JSON 序列化失败", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (StrUtil.isBlank(json)) {
            return fallback;
        }
        try {
            return this.objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private static InvestorPsychProfileQuestionnaireVO.Question question(
            String code,
            String title,
            InvestorPsychProfileQuestionnaireVO.Option... options) {
        return new InvestorPsychProfileQuestionnaireVO.Question(code, title, List.of(options));
    }

    private static InvestorPsychProfileQuestionnaireVO.Option option(String code, String label) {
        return new InvestorPsychProfileQuestionnaireVO.Option(code, label);
    }
}
