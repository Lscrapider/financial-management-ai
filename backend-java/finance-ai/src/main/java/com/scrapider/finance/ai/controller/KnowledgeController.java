package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.KnowledgeChunkPageVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeStatsVO;
import com.scrapider.finance.ai.service.KnowledgeService;
import com.scrapider.finance.domain.param.KnowledgeChunkUpdateParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStatsVO> stats() {
        return ResponseEntity.ok(this.knowledgeService.stats());
    }

    @GetMapping("/chunks")
    public ResponseEntity<KnowledgeChunkPageVO> chunks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String filename) {
        return ResponseEntity.ok(this.knowledgeService.pageChunks(pageNum, pageSize, filename));
    }

    @GetMapping("/chunks/{id}")
    public ResponseEntity<KnowledgeChunkVO> chunkDetail(@PathVariable Long id) {
        return ResponseEntity.ok(this.knowledgeService.chunkDetail(id));
    }

    @PutMapping("/chunks/{id}")
    public ResponseEntity<KnowledgeChunkVO> updateChunk(
            @PathVariable Long id,
            @RequestBody KnowledgeChunkUpdateParam param) {
        return ResponseEntity.ok(this.knowledgeService.updateChunk(id, param));
    }
}
