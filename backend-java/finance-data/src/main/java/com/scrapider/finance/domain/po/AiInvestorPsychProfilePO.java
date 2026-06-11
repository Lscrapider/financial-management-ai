package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_investor_psych_profile")
public class AiInvestorPsychProfilePO {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";

    private Long id;
    private Long userId;
    private Long profileVersion;
    private String status;
    private String riskEmotion;
    private String decisionStyle;
    private String holdingMindsetJson;
    private String tradingTempo;
    private String explanationPreference;
    private String adviceStyle;
    private String rawAdviceStyle;
    private String tagScoresJson;
    private String questionnaireAnswersJson;
    private String summary;
    private Boolean confirmedByUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiInvestorPsychProfilePO createActive(
            Long userId,
            Long profileVersion,
            String riskEmotion,
            String decisionStyle,
            String holdingMindsetJson,
            String tradingTempo,
            String explanationPreference,
            String adviceStyle,
            String rawAdviceStyle,
            String tagScoresJson,
            String questionnaireAnswersJson,
            String summary) {
        LocalDateTime now = LocalDateTime.now();
        AiInvestorPsychProfilePO profile = new AiInvestorPsychProfilePO();
        profile.setUserId(userId);
        profile.setProfileVersion(profileVersion);
        profile.setStatus(STATUS_ACTIVE);
        profile.setRiskEmotion(riskEmotion);
        profile.setDecisionStyle(decisionStyle);
        profile.setHoldingMindsetJson(holdingMindsetJson);
        profile.setTradingTempo(tradingTempo);
        profile.setExplanationPreference(explanationPreference);
        profile.setAdviceStyle(adviceStyle);
        profile.setRawAdviceStyle(rawAdviceStyle);
        profile.setTagScoresJson(tagScoresJson);
        profile.setQuestionnaireAnswersJson(questionnaireAnswersJson);
        profile.setSummary(summary);
        profile.setConfirmedByUser(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profile;
    }
}
