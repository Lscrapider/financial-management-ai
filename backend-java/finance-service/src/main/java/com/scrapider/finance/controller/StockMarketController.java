package com.scrapider.finance.controller;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockKlineParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockKlineVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.service.StockMarketQueryService;
import com.scrapider.finance.task.StockMarketSyncTask;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/klines")
    public ResponseEntity<List<StockKlineVO>> listKlines(@ModelAttribute StockKlineParam param) {
        return ResponseEntity.ok(this.stockMarketQueryService.listKlines(param));
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

    /**
     * 按需同步单只股票的分时走势数据。
     * 前端在股票行情选择某只股票后可调用，只拉取该股的分时数据。
     */
    @PostMapping("/sync/trends/{stockCode}")
    public ApiResponseVO<MarketSyncStatusVO> syncStockTrend(@PathVariable String stockCode) {
        if (StrUtil.isBlank(stockCode)) {
            return ApiResponseVO.error(400, "stockCode must not be blank");
        }
        boolean submitted = this.stockMarketSyncTask.syncStockTrend(stockCode.trim());
        return submitted
                ? ApiResponseVO.success(new MarketSyncStatusVO(true, false))
                : ApiResponseVO.error(404, "股票不存在或未启用: " + stockCode);
    }

    @PostMapping("/sync/daily-klines/{stockCode}")
    public ApiResponseVO<MarketSyncStatusVO> syncStockDailyKline(@PathVariable String stockCode) {
        if (StrUtil.isBlank(stockCode)) {
            return ApiResponseVO.error(400, "stockCode must not be blank");
        }
        boolean submitted = this.stockMarketSyncTask.syncStockDailyKline(stockCode.trim());
        return submitted
                ? ApiResponseVO.success(new MarketSyncStatusVO(true, false))
                : ApiResponseVO.error(404, "股票不存在或未启用: " + stockCode);
    }
}
