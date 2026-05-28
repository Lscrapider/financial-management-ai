package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.IndexDailyKlineParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.IndexDailyKlineVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.service.IndexMarketQueryService;
import com.scrapider.finance.task.IndexMarketSyncTask;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indices")
public class IndexMarketController {

    private final IndexMarketQueryService indexMarketQueryService;
    private final IndexMarketSyncTask indexMarketSyncTask;

    public IndexMarketController(
            IndexMarketQueryService indexMarketQueryService,
            IndexMarketSyncTask indexMarketSyncTask) {
        this.indexMarketQueryService = indexMarketQueryService;
        this.indexMarketSyncTask = indexMarketSyncTask;
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<IndexQuoteVO>> listQuotes(@ModelAttribute IndexQuoteListParam param) {
        return ResponseEntity.ok(this.indexMarketQueryService.listQuotes(param));
    }

    @GetMapping("/daily-klines")
    public ResponseEntity<List<IndexDailyKlineVO>> listDailyKlines(@ModelAttribute IndexDailyKlineParam param) {
        return ResponseEntity.ok(this.indexMarketQueryService.listDailyKlines(param));
    }

    @PostMapping("/sync")
    public ApiResponseVO<MarketSyncStatusVO> syncIndices() {
        boolean started = this.indexMarketSyncTask.startManualSync();
        return ApiResponseVO.success(new MarketSyncStatusVO(started, this.indexMarketSyncTask.isSyncing()));
    }

    @GetMapping("/sync/status")
    public ApiResponseVO<MarketSyncStatusVO> indexSyncStatus() {
        return ApiResponseVO.success(new MarketSyncStatusVO(false, this.indexMarketSyncTask.isSyncing()));
    }
}
