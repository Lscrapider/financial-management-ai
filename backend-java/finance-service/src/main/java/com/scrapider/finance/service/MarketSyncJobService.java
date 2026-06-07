package com.scrapider.finance.service;

import com.scrapider.finance.domain.vo.MarketSyncJobVO;
import java.util.List;

public interface MarketSyncJobService {

    List<MarketSyncJobVO> listLatestFullJobs();
}
