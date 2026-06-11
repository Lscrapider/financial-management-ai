package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record InvestorPsychProfileQuestionnaireVO(
        List<Question> questions) {

    public record Question(
            String code,
            String title,
            List<Option> options) {
    }

    public record Option(
            String code,
            String label) {
    }
}
