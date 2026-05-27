package com.scrapider.finance.controller;

import com.scrapider.finance.domain.vo.ApiResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.scrapider.finance.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed.", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseVO.error("Username or password is incorrect."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Bad request.", ex);
        return ResponseEntity.badRequest().body(ApiResponseVO.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseVO<Void>> handleException(Exception ex) {
        log.error("Unexpected server error.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseVO.error(ex.getMessage()));
    }
}
