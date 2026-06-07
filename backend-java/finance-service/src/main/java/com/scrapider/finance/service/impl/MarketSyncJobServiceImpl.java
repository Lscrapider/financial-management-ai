package com.scrapider.finance.service.impl;

import com.scrapider.finance.converter.MarketSyncJobConverter;
import com.scrapider.finance.domain.po.MarketSyncJobPO;
import com.scrapider.finance.domain.vo.MarketSyncJobVO;
import com.scrapider.finance.manage.MarketSyncJobManage;
import com.scrapider.finance.service.MarketSyncJobService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketSyncJobServiceImpl implements MarketSyncJobService {

    private final MarketSyncJobManage marketSyncJobManage;

    public MarketSyncJobServiceImpl(MarketSyncJobManage marketSyncJobManage) {
        this.marketSyncJobManage = marketSyncJobManage;
    }

    @Override
    public List<MarketSyncJobVO> listLatestFullJobs() {
        Map<String, MarketSyncJobPO> latestByTargetType = new LinkedHashMap<>();
        for (MarketSyncJobPO job : this.marketSyncJobManage.listRecentFullJobs(30)) {
            latestByTargetType.putIfAbsent(job.getTargetType(), job);
        }
        return latestByTargetType.values().stream()
                .map(MarketSyncJobConverter::toVO)
                .toList();
    }
}
