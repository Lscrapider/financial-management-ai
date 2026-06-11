package com.scrapider.finance.ai.domain.vo;

import java.util.List;
import java.util.Map;

public record InvestorPsychProfileVO(
        Boolean profileCompleted,
        Long profileVersion,
        String riskEmotion,
        String decisionStyle,
        List<String> holdingMindset,
        String tradingTempo,
        String explanationPreference,
        String adviceStyle,
        String rawAdviceStyle,
        Map<String, Integer> tagScores,
        String summary) {

    public static InvestorPsychProfileVO empty() {
        return new InvestorPsychProfileVO(
                false,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                Map.of(),
                null);
    }
}
