package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisTargetOptionVO;
import com.scrapider.finance.ai.service.SceneAnalysisMetadataService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/scene-analysis/targets")
public class SceneAnalysisTargetController {

    private final SceneAnalysisMetadataService sceneAnalysisMetadataService;

    public SceneAnalysisTargetController(SceneAnalysisMetadataService sceneAnalysisMetadataService) {
        this.sceneAnalysisMetadataService = sceneAnalysisMetadataService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<SceneAnalysisTargetOptionVO>> search(
            @RequestParam(defaultValue = "STOCK") String targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.search(targetType, keyword, limit));
    }
}
