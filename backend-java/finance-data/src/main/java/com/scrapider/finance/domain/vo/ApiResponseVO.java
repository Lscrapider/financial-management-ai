package com.scrapider.finance.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponseVO<T> {

    private Integer code;
    private T data;
    private String error;
    private String message;

    public static <T> ApiResponseVO<T> success(T data) {
        return new ApiResponseVO<>(0, data, null, "ok");
    }

    public static <T> ApiResponseVO<T> error(String message) {
        return new ApiResponseVO<>(-1, null, message, message);
    }

    public static <T> ApiResponseVO<T> error(Integer code, String message) {
        return new ApiResponseVO<>(code, null, message, message);
    }
}
