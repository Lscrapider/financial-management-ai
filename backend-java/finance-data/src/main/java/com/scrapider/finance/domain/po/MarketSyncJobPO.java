package com.scrapider.finance.domain.po;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@TableName("market_sync_job")
public class MarketSyncJobPO {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String SYNC_MODE_FULL = "full";
    public static final String DATA_SCOPE_ALL = "all";
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_SCHEDULED = "scheduled";

    private Long id;
    private String jobNo;
    private String targetType;
    private String syncMode;
    private String dataScope;
    private String triggerType;
    private String targetCode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MarketSyncJobPO createRunning(
            String targetType,
            String syncMode,
            String dataScope,
            String triggerType,
            String targetCode) {
        LocalDateTime now = LocalDateTime.now();
        MarketSyncJobPO job = new MarketSyncJobPO();
        job.setJobNo(UUID.randomUUID().toString());
        job.setTargetType(targetType);
        job.setSyncMode(syncMode);
        job.setDataScope(dataScope);
        job.setTriggerType(triggerType);
        job.setTargetCode(StrUtil.isBlank(targetCode) ? null : targetCode);
        job.setStatus(STATUS_RUNNING);
        job.setStartedAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }

    public void markSuccess() {
        this.finish(STATUS_SUCCESS, null);
    }

    public void markFailed(String message) {
        this.finish(STATUS_FAILED, message);
    }

    private void finish(String status, String message) {
        LocalDateTime now = LocalDateTime.now();
        this.setStatus(status);
        this.setFinishedAt(now);
        this.setUpdatedAt(now);
        if (this.startedAt != null) {
            this.setDurationMs(Duration.between(this.startedAt, now).toMillis());
        }
        String limitedMessage = StrUtil.maxLength(message, 1000);
        this.setErrorMessage(StrUtil.isBlank(limitedMessage) ? null : limitedMessage);
    }
}
