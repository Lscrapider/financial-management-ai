package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.security.SceneAnalysisCallbackTokenStore;
import com.scrapider.finance.ai.service.SceneAnalysisReportService;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/scene-analysis/tasks")
public class SceneAnalysisTaskController {

    private final SceneAnalysisTaskService sceneAnalysisTaskService;
    private final SceneAnalysisReportService sceneAnalysisReportService;
    private final SceneAnalysisCallbackTokenStore callbackTokenStore;

    public SceneAnalysisTaskController(
            SceneAnalysisTaskService sceneAnalysisTaskService,
            SceneAnalysisReportService sceneAnalysisReportService,
            SceneAnalysisCallbackTokenStore callbackTokenStore) {
        this.sceneAnalysisTaskService = sceneAnalysisTaskService;
        this.sceneAnalysisReportService = sceneAnalysisReportService;
        this.callbackTokenStore = callbackTokenStore;
    }

    @PostMapping
    public ResponseEntity<SceneAnalysisSubmitVO> submit(@RequestBody SceneAnalysisSubmitParam param) {
        return ResponseEntity.ok(this.sceneAnalysisTaskService.submit(param));
    }

    @PostMapping("/{taskNo}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String taskNo,
            @RequestHeader(value = SceneAnalysisCallbackTokenStore.HEADER_NAME, required = false) String callbackToken,
            @RequestBody SceneAnalysisCallbackParam param) {
        if (!this.callbackTokenStore.matches(taskNo, callbackToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        this.sceneAnalysisTaskService.callback(taskNo, callbackToken, param);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskNo}/report/regenerate")
    public ResponseEntity<Void> regenerateReport(@PathVariable String taskNo) {
        this.sceneAnalysisReportService.regenerateFromStoredContext(taskNo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskNo}/report")
    public ResponseEntity<SceneAnalysisReportVO> report(@PathVariable String taskNo) {
        return ResponseEntity.ok(this.sceneAnalysisReportService.getReport(taskNo));
    }

    @GetMapping("/reports/targets")
    public ResponseEntity<SceneAnalysisReportTargetPageVO> reportTargets(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String targetCode,
            @RequestParam(required = false) String targetType) {
        return ResponseEntity.ok(this.sceneAnalysisReportService
                .pageTargets(pageNum, pageSize, targetName, targetCode, targetType));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<SceneAnalysisReportHistoryVO>> reports(
            @RequestParam String targetType,
            @RequestParam String targetCode) {
        return ResponseEntity.ok(this.sceneAnalysisReportService.listHistory(targetType, targetCode));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<SceneAnalysisReportDetailVO> reportDetail(@PathVariable Long reportId) {
        return ResponseEntity.ok(this.sceneAnalysisReportService.detail(reportId));
    }
}
