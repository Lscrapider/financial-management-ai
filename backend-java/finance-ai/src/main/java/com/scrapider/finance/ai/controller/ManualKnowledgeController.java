package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.ManualKnowledgeDraftParam;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.service.ManualKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/manual-knowledge/tasks")
public class ManualKnowledgeController {

    private final ManualKnowledgeService manualKnowledgeService;

    public ManualKnowledgeController(ManualKnowledgeService manualKnowledgeService) {
        this.manualKnowledgeService = manualKnowledgeService;
    }

    @PostMapping("/page")
    public ResponseEntity<OcrTaskPageVO> page(@RequestBody(required = false) OcrTaskPageParam param) {
        return ResponseEntity.ok(this.manualKnowledgeService.page(param));
    }

    @PostMapping
    public ResponseEntity<OcrTaskVO> create(@RequestBody ManualKnowledgeDraftParam param) {
        return ResponseEntity.ok(this.manualKnowledgeService.createDraft(param));
    }

    @GetMapping("/{taskNo}")
    public ResponseEntity<OcrReviewVO> detail(@PathVariable String taskNo) {
        return ResponseEntity.ok(this.manualKnowledgeService.detail(taskNo));
    }

    @PutMapping("/{taskNo}/draft")
    public ResponseEntity<Void> saveDraft(@PathVariable String taskNo, @RequestBody ManualKnowledgeDraftParam param) {
        this.manualKnowledgeService.saveDraft(taskNo, param);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskNo}/submit")
    public ResponseEntity<Void> submit(@PathVariable String taskNo, @RequestBody ManualKnowledgeDraftParam param) {
        this.manualKnowledgeService.submit(taskNo, param);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody OcrTaskDeleteParam param) {
        this.manualKnowledgeService.delete(param);
        return ResponseEntity.ok().build();
    }
}
