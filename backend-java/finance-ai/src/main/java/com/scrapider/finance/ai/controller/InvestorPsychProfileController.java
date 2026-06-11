package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.InvestorPsychProfileSubmitParam;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileQuestionnaireVO;
import com.scrapider.finance.ai.domain.vo.InvestorPsychProfileVO;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.service.InvestorPsychProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/investor-psych-profile")
public class InvestorPsychProfileController {

    private final InvestorPsychProfileService investorPsychProfileService;

    public InvestorPsychProfileController(InvestorPsychProfileService investorPsychProfileService) {
        this.investorPsychProfileService = investorPsychProfileService;
    }

    @GetMapping("/questionnaire")
    public ResponseEntity<InvestorPsychProfileQuestionnaireVO> questionnaire() {
        return ResponseEntity.ok(this.investorPsychProfileService.questionnaire());
    }

    @GetMapping
    public ResponseEntity<InvestorPsychProfileVO> current() {
        return ResponseEntity.ok(this.investorPsychProfileService.current(CurrentUserContext.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<InvestorPsychProfileVO> submit(@RequestBody InvestorPsychProfileSubmitParam param) {
        return ResponseEntity.ok(this.investorPsychProfileService.submit(CurrentUserContext.currentUserId(), param));
    }

    @PutMapping
    public ResponseEntity<InvestorPsychProfileVO> update(@RequestBody InvestorPsychProfileSubmitParam param) {
        return ResponseEntity.ok(this.investorPsychProfileService.submit(CurrentUserContext.currentUserId(), param));
    }
}
