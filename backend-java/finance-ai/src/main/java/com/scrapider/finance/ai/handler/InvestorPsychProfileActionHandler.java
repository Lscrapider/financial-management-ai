package com.scrapider.finance.ai.handler;

import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.ai.service.InvestorPsychProfileService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InvestorPsychProfileActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "investor.psych_profile";

    private final InvestorPsychProfileService investorPsychProfileService;

    public InvestorPsychProfileActionHandler(InvestorPsychProfileService investorPsychProfileService) {
        this.investorPsychProfileService = investorPsychProfileService;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        InvestorPsychProfileVO profile = this.investorPsychProfileService.current(session.userId());
        boolean profileExists = Boolean.TRUE.equals(profile.profileCompleted());
        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                profileExists ? List.of(this.profileData(profile)) : List.of(),
                Map.of(
                        "queriedAt", OffsetDateTime.now().toString(),
                        "profileExists", profileExists),
                null);
    }

    private Map<String, Object> profileData(InvestorPsychProfileVO profile) {
        return Map.of(
                "profileExists", true,
                "profileVersion", profile.profileVersion(),
                "riskEmotion", profile.riskEmotion(),
                "decisionStyle", profile.decisionStyle(),
                "holdingMindset", profile.holdingMindset(),
                "tradingTempo", profile.tradingTempo(),
                "explanationPreference", profile.explanationPreference(),
                "adviceStyle", profile.adviceStyle(),
                "summary", profile.summary() == null ? "" : profile.summary());
    }
}
