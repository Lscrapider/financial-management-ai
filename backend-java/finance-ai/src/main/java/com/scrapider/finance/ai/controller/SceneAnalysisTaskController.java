package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.service.SceneAnalysisReportGenerationService;
import com.scrapider.finance.ai.service.SceneAnalysisReportQueryService;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/scene-analysis/tasks")
public class SceneAnalysisTaskController {

    private final SceneAnalysisTaskService sceneAnalysisTaskService;
    private final SceneAnalysisReportGenerationService sceneAnalysisReportGenerationService;
    private final SceneAnalysisReportQueryService sceneAnalysisReportQueryService;

    public SceneAnalysisTaskController(
            SceneAnalysisTaskService sceneAnalysisTaskService,
            SceneAnalysisReportGenerationService sceneAnalysisReportGenerationService,
            SceneAnalysisReportQueryService sceneAnalysisReportQueryService) {
        this.sceneAnalysisTaskService = sceneAnalysisTaskService;
        this.sceneAnalysisReportGenerationService = sceneAnalysisReportGenerationService;
        this.sceneAnalysisReportQueryService = sceneAnalysisReportQueryService;
    }

    @PostMapping
    public ResponseEntity<SceneAnalysisSubmitVO> submit(@RequestBody SceneAnalysisSubmitParam param) {
        return ResponseEntity.ok(this.sceneAnalysisTaskService.submit(param));
    }

    @PostMapping("/{taskNo}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String taskNo,
            @RequestBody SceneAnalysisCallbackParam param) {
        this.sceneAnalysisTaskService.callback(taskNo, param);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskNo}/report/regenerate")
    public ResponseEntity<Void> regenerateReport(@PathVariable String taskNo) {
        this.sceneAnalysisReportGenerationService.regenerateFromStoredContext(taskNo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskNo}/report")
    public ResponseEntity<SceneAnalysisReportVO> report(@PathVariable String taskNo) {
        return ResponseEntity.ok(this.sceneAnalysisReportGenerationService.getReport(taskNo));
    }

    @GetMapping("/reports/targets")
    public ResponseEntity<SceneAnalysisReportTargetPageVO> reportTargets(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String targetCode,
            @RequestParam(required = false) String targetType) {
        return ResponseEntity.ok(this.sceneAnalysisReportQueryService
                .pageTargets(pageNum, pageSize, targetName, targetCode, targetType));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<SceneAnalysisReportHistoryVO>> reports(
            @RequestParam String targetType,
            @RequestParam String targetCode) {
        return ResponseEntity.ok(this.sceneAnalysisReportQueryService.listHistory(targetType, targetCode));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<SceneAnalysisReportDetailVO> reportDetail(@PathVariable Long reportId) {
        return ResponseEntity.ok(this.sceneAnalysisReportQueryService.detail(reportId));
    }
}
