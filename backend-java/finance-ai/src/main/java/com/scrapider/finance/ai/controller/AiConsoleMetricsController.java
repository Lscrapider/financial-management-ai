package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.AiConsoleOverviewVO;
import com.scrapider.finance.ai.domain.vo.AppVisitTrendVO;
import com.scrapider.finance.ai.service.AiConsoleMetricsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/console")
public class AiConsoleMetricsController {

    private final AiConsoleMetricsService aiConsoleMetricsService;

    public AiConsoleMetricsController(AiConsoleMetricsService aiConsoleMetricsService) {
        this.aiConsoleMetricsService = aiConsoleMetricsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AiConsoleOverviewVO> overview(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(this.aiConsoleMetricsService.overview(days));
    }

    @GetMapping("/visit-trends")
    public ResponseEntity<List<AppVisitTrendVO>> visitTrends(@RequestParam(required = false) Integer hours) {
        return ResponseEntity.ok(this.aiConsoleMetricsService.visitTrends(hours));
    }
}
