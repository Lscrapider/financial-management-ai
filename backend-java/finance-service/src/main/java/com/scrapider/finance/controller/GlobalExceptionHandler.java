package com.scrapider.finance.controller;

import com.scrapider.finance.domain.exception.BusinessException;
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

    private static final String BAD_REQUEST_MESSAGE = "请求参数不正确。";
    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误，请稍后再试。";

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed.", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseVO.error("用户名或密码不正确。"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business request failed.", ex);
        return ResponseEntity.badRequest().body(ApiResponseVO.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Bad request.", ex);
        return ResponseEntity.badRequest().body(ApiResponseVO.error(BAD_REQUEST_MESSAGE));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseVO<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.error("Unexpected server state.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseVO.error(INTERNAL_ERROR_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseVO<Void>> handleException(Exception ex) {
        log.error("Unexpected server error.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseVO.error(INTERNAL_ERROR_MESSAGE));
    }
}
