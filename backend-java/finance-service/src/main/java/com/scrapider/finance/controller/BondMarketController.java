package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.BondDailyKlineParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.BondDailyKlineVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.service.BondMarketQueryService;
import com.scrapider.finance.task.BondMarketSyncTask;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bonds")
public class BondMarketController {

    private final BondMarketQueryService bondMarketQueryService;
    private final BondMarketSyncTask bondMarketSyncTask;

    public BondMarketController(
            BondMarketQueryService bondMarketQueryService,
            BondMarketSyncTask bondMarketSyncTask) {
        this.bondMarketQueryService = bondMarketQueryService;
        this.bondMarketSyncTask = bondMarketSyncTask;
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<BondQuoteVO>> listQuotes(@ModelAttribute BondQuoteListParam param) {
        return ResponseEntity.ok(this.bondMarketQueryService.listQuotes(param));
    }

    @GetMapping("/daily-klines")
    public ResponseEntity<List<BondDailyKlineVO>> listDailyKlines(@ModelAttribute BondDailyKlineParam param) {
        return ResponseEntity.ok(this.bondMarketQueryService.listDailyKlines(param));
    }

    @PostMapping("/sync")
    public ApiResponseVO<MarketSyncStatusVO> syncBonds() {
        boolean started = this.bondMarketSyncTask.startManualSync();
        return ApiResponseVO.success(new MarketSyncStatusVO(started, this.bondMarketSyncTask.isSyncing()));
    }

    @GetMapping("/sync/status")
    public ApiResponseVO<MarketSyncStatusVO> bondSyncStatus() {
        return ApiResponseVO.success(new MarketSyncStatusVO(false, this.bondMarketSyncTask.isSyncing()));
    }
}
