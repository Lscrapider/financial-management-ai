package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.AiTokenUsageOverviewVO;
import com.scrapider.finance.ai.domain.vo.AiTokenUsageTrendVO;
import com.scrapider.finance.ai.service.AiTokenUsageService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/token-usage")
public class AiTokenUsageController {

    private final AiTokenUsageService aiTokenUsageService;

    public AiTokenUsageController(AiTokenUsageService aiTokenUsageService) {
        this.aiTokenUsageService = aiTokenUsageService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AiTokenUsageOverviewVO> overview(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(this.aiTokenUsageService.overview(days));
    }

    @GetMapping("/trends")
    public ResponseEntity<List<AiTokenUsageTrendVO>> trends(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(this.aiTokenUsageService.trends(days));
    }
}
