package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.SceneAnalysisConfigProfileParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigGroupVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigProfileVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTypeVO;
import com.scrapider.finance.ai.service.SceneAnalysisMetadataService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/scene-analysis/config-profiles")
public class SceneAnalysisConfigProfileController {

    private final SceneAnalysisMetadataService sceneAnalysisMetadataService;

    public SceneAnalysisConfigProfileController(SceneAnalysisMetadataService sceneAnalysisMetadataService) {
        this.sceneAnalysisMetadataService = sceneAnalysisMetadataService;
    }

    @GetMapping
    public ResponseEntity<List<SceneAnalysisConfigProfileVO>> listProfiles() {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.listProfiles());
    }

    @GetMapping("/parameter-schema")
    public ResponseEntity<List<SceneAnalysisConfigGroupVO>> parameterSchema() {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.parameterSchema());
    }

    @GetMapping("/report-types")
    public ResponseEntity<List<SceneAnalysisReportTypeVO>> reportTypes() {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.reportTypes());
    }

    @PostMapping
    public ResponseEntity<SceneAnalysisConfigProfileVO> create(@RequestBody SceneAnalysisConfigProfileParam param) {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.create(param));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SceneAnalysisConfigProfileVO> update(
            @PathVariable Long id,
            @RequestBody SceneAnalysisConfigProfileParam param) {
        return ResponseEntity.ok(this.sceneAnalysisMetadataService.update(id, param));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        this.sceneAnalysisMetadataService.delete(id);
        return ResponseEntity.ok().build();
    }
}
