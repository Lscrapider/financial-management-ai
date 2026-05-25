package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.ErrorResponseVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.service.OcrTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/ocr/tasks")
public class OcrTaskController {

    private final OcrTaskService ocrTaskService;

    public OcrTaskController(OcrTaskService ocrTaskService) {
        this.ocrTaskService = ocrTaskService;
    }

    @PostMapping
    public ResponseEntity<OcrTaskVO> submit(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(this.ocrTaskService.submit(file));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponseVO> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseVO(ex.getMessage()));
    }
}
