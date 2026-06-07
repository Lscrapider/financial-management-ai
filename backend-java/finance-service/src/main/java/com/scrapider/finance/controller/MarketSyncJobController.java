package com.scrapider.finance.controller;

import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.MarketSyncJobVO;
import com.scrapider.finance.service.MarketSyncJobService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-sync/jobs")
public class MarketSyncJobController {

    private final MarketSyncJobService marketSyncJobService;

    public MarketSyncJobController(MarketSyncJobService marketSyncJobService) {
        this.marketSyncJobService = marketSyncJobService;
    }

    @GetMapping("/latest-full")
    public ApiResponseVO<List<MarketSyncJobVO>> listLatestFullJobs() {
        return ApiResponseVO.success(this.marketSyncJobService.listLatestFullJobs());
    }
}
