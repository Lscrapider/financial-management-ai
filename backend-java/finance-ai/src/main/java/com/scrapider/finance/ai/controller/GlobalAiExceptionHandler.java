package com.scrapider.finance.ai.controller;

import com.scrapider.finance.ai.domain.vo.ErrorResponseVO;
import com.scrapider.finance.domain.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.scrapider.finance.ai.controller")
public class GlobalAiExceptionHandler {

    private static final String BAD_REQUEST_MESSAGE = "请求参数不正确。";
    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误，请稍后再试。";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseVO> handleBusinessException(BusinessException ex) {
        log.warn("AI module business request failed.", ex);
        return ResponseEntity.badRequest().body(new ErrorResponseVO(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseVO> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("AI module bad request.", ex);
        return ResponseEntity.badRequest().body(new ErrorResponseVO(BAD_REQUEST_MESSAGE));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseVO> handleIllegalStateException(IllegalStateException ex) {
        log.error("AI module unexpected server state.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseVO(INTERNAL_ERROR_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseVO> handleException(Exception ex) {
        log.error("AI module unexpected server error.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseVO(INTERNAL_ERROR_MESSAGE));
    }
}
