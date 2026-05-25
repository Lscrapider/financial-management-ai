package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.ErrorResponseVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;
import com.scrapider.finance.ai.service.OcrTaskService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/ocr/tasks")
public class OcrTaskController {

    private final OcrTaskService ocrTaskService;

    public OcrTaskController(OcrTaskService ocrTaskService) {
        this.ocrTaskService = ocrTaskService;
    }

    @GetMapping
    public ResponseEntity<List<OcrTaskVO>> listRecent(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(this.ocrTaskService.listRecent(limit));
    }

    @PostMapping
    public ResponseEntity<List<OcrTaskVO>> submit(@RequestPart("file") List<MultipartFile> files) {
        return ResponseEntity.ok(this.ocrTaskService.submit(files));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponseVO> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseVO(ex.getMessage()));
    }
}
