package com.scrapider.finance.controller;

import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.service.StockMarketQueryService;
import com.scrapider.finance.task.StockMarketSyncTask;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockMarketController {

    private final StockMarketQueryService stockMarketQueryService;
    private final StockMarketSyncTask stockMarketSyncTask;

    public StockMarketController(
            StockMarketQueryService stockMarketQueryService,
            StockMarketSyncTask stockMarketSyncTask) {
        this.stockMarketQueryService = stockMarketQueryService;
        this.stockMarketSyncTask = stockMarketSyncTask;
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<StockQuoteVO>> listQuotes(@ModelAttribute StockQuoteListParam param) {
        return ResponseEntity.ok(this.stockMarketQueryService.listQuotes(param));
    }

    @GetMapping("/intraday-trends")
    public ResponseEntity<List<StockIntradayTrendVO>> listIntradayTrends(
            @ModelAttribute StockIntradayTrendParam param) {
        return ResponseEntity.ok(this.stockMarketQueryService.listIntradayTrends(param));
    }

    @PostMapping("/sync")
    public ApiResponseVO<MarketSyncStatusVO> syncStocks() {
        boolean started = this.stockMarketSyncTask.startManualSync();
        return ApiResponseVO.success(new MarketSyncStatusVO(started, this.stockMarketSyncTask.isSyncing()));
    }

    @GetMapping("/sync/status")
    public ApiResponseVO<MarketSyncStatusVO> stockSyncStatus() {
        return ApiResponseVO.success(new MarketSyncStatusVO(false, this.stockMarketSyncTask.isSyncing()));
    }
}
