package com.scrapider.finance.ai.domain.param;

import java.util.List;

public record InvestorPsychProfileSubmitParam(
        List<InvestorPsychProfileAnswerParam> answers,
        String adviceStyle) {

    public InvestorPsychProfileSubmitParam(List<InvestorPsychProfileAnswerParam> answers) {
        this(answers, null);
    }
}
