package com.scrapider.finance.converter;

import com.scrapider.finance.domain.po.MarketSyncJobPO;
import com.scrapider.finance.domain.vo.MarketSyncJobVO;
import java.time.format.DateTimeFormatter;

public final class MarketSyncJobConverter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MarketSyncJobConverter() {
    }

    public static MarketSyncJobVO toVO(MarketSyncJobPO job) {
        return new MarketSyncJobVO(
                job.getJobNo(),
                job.getTargetType(),
                job.getSyncMode(),
                job.getDataScope(),
                job.getTriggerType(),
                job.getTargetCode(),
                job.getStatus(),
                job.getStartedAt() == null ? null : job.getStartedAt().format(FORMATTER),
                job.getFinishedAt() == null ? null : job.getFinishedAt().format(FORMATTER),
                job.getDurationMs(),
                job.getErrorMessage());
    }
}
