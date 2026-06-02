package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.ai.service.SceneAnalysisTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/scene-analysis/tasks")
public class SceneAnalysisTaskController {

    private final SceneAnalysisTaskService sceneAnalysisTaskService;

    public SceneAnalysisTaskController(SceneAnalysisTaskService sceneAnalysisTaskService) {
        this.sceneAnalysisTaskService = sceneAnalysisTaskService;
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
}
