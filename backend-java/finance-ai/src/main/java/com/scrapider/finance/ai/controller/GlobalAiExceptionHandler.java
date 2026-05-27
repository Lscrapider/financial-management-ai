package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.ErrorResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.scrapider.finance.ai.controller")
public class GlobalAiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponseVO> handleBadRequest(RuntimeException ex) {
        log.warn("AI module bad request.", ex);
        return ResponseEntity.badRequest().body(new ErrorResponseVO(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseVO> handleException(Exception ex) {
        log.error("AI module unexpected server error.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseVO(ex.getMessage()));
    }
}
