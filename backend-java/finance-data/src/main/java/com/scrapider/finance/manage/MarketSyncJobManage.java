package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.MarketSyncJobPO;
import com.scrapider.finance.mapper.MarketSyncJobMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketSyncJobManage extends ServiceImpl<MarketSyncJobMapper, MarketSyncJobPO> {

    public MarketSyncJobPO startFullJob(String targetType, String triggerType) {
        MarketSyncJobPO job = MarketSyncJobPO.createRunning(
                targetType,
                MarketSyncJobPO.SYNC_MODE_FULL,
                MarketSyncJobPO.DATA_SCOPE_ALL,
                triggerType,
                null);
        this.save(job);
        return job;
    }

    public void markSuccess(Long id) {
        MarketSyncJobPO job = this.getById(id);
        if (job == null) {
            return;
        }
        job.markSuccess();
        this.updateById(job);
    }

    public void markFailed(Long id, String errorMessage) {
        MarketSyncJobPO job = this.getById(id);
        if (job == null) {
            return;
        }
        job.markFailed(errorMessage);
        this.updateById(job);
    }

    public List<MarketSyncJobPO> listRecentFullJobs(int limit) {
        return this.list(new LambdaQueryWrapper<MarketSyncJobPO>()
                .eq(MarketSyncJobPO::getSyncMode, MarketSyncJobPO.SYNC_MODE_FULL)
                .orderByDesc(MarketSyncJobPO::getStartedAt)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
