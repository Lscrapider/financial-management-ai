package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.KnowledgeMaterialSearchParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialSubmitVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialTaskVO;
import com.scrapider.finance.ai.security.SceneAnalysisCallbackTokenStore;
import com.scrapider.finance.ai.service.KnowledgeMaterialSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/knowledge-material/tasks")
public class KnowledgeMaterialSearchController {

    private final KnowledgeMaterialSearchService knowledgeMaterialSearchService;
    private final SceneAnalysisCallbackTokenStore callbackTokenStore;

    public KnowledgeMaterialSearchController(
            KnowledgeMaterialSearchService knowledgeMaterialSearchService,
            SceneAnalysisCallbackTokenStore callbackTokenStore) {
        this.knowledgeMaterialSearchService = knowledgeMaterialSearchService;
        this.callbackTokenStore = callbackTokenStore;
    }

    @PostMapping
    public ResponseEntity<KnowledgeMaterialSubmitVO> submit(@RequestBody KnowledgeMaterialSearchParam param) {
        return ResponseEntity.ok(this.knowledgeMaterialSearchService.submit(param));
    }

    @PostMapping("/{taskNo}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String taskNo,
            @RequestHeader(value = SceneAnalysisCallbackTokenStore.HEADER_NAME, required = false) String callbackToken,
            @RequestBody SceneAnalysisCallbackParam param) {
        if (!this.callbackTokenStore.matches(taskNo, callbackToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        this.knowledgeMaterialSearchService.callback(taskNo, callbackToken, param);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskNo}")
    public ResponseEntity<KnowledgeMaterialTaskVO> detail(@PathVariable String taskNo) {
        return ResponseEntity.ok(this.knowledgeMaterialSearchService.detail(taskNo));
    }
}
