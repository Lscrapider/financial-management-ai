package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.param.OcrReviewDraftParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.service.OcrReviewService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/ocr/reviews")
public class OcrReviewController {

    private final OcrReviewService ocrReviewService;

    public OcrReviewController(OcrReviewService ocrReviewService) {
        this.ocrReviewService = ocrReviewService;
    }

    @GetMapping("/{taskNo}")
    public ResponseEntity<OcrReviewVO> detail(@PathVariable String taskNo) {
        return ResponseEntity.ok(this.ocrReviewService.detail(taskNo));
    }

    @PutMapping("/{taskNo}/draft")
    public ResponseEntity<Void> saveDraft(
            @PathVariable String taskNo,
            @RequestBody OcrReviewDraftParam param) {
        this.ocrReviewService.saveDraft(taskNo, param);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskNo}/submit")
    public ResponseEntity<Void> submit(
            @PathVariable String taskNo,
            @RequestBody(required = false) OcrReviewDraftParam param) {
        this.ocrReviewService.submit(taskNo, param);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{taskNo}/pages/{pageNo}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> pageImage(@PathVariable String taskNo, @PathVariable Integer pageNo) {
        return ResponseEntity.ok(this.ocrReviewService.pageImage(taskNo, pageNo));
    }
}
