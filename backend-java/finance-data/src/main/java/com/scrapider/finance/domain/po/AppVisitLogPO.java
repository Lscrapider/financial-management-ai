package com.scrapider.finance.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_visit_log")
public class AppVisitLogPO {

    private Long id;
    private String username;
    private String requestMethod;
    private String requestUri;
    private Integer statusCode;
    private Long durationMs;
    private String remoteAddr;
    private String userAgent;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
