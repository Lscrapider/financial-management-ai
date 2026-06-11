package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.InvestorPsychProfileSubmitParam;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileQuestionnaireVO;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileVO;

public interface InvestorPsychProfileService {

    InvestorPsychProfileQuestionnaireVO questionnaire();

    InvestorPsychProfileVO current(Long userId);

    InvestorPsychProfileVO submit(Long userId, InvestorPsychProfileSubmitParam param);
}
