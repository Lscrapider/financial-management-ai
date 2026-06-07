package com.scrapider.finance.controller;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.param.IndexKlineParam;
import com.scrapider.finance.domain.param.IndexIntradayTrendParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.IndexKlineVO;
import com.scrapider.finance.domain.vo.IndexIntradayTrendVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.service.IndexMarketQueryService;
import com.scrapider.finance.task.IndexMarketSyncTask;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/intraday-trends")
    public ResponseEntity<List<IndexIntradayTrendVO>> listIntradayTrends(
            @ModelAttribute IndexIntradayTrendParam param) {
        return ResponseEntity.ok(this.indexMarketQueryService.listIntradayTrends(param));
    }

    @GetMapping("/klines")
    public ResponseEntity<List<IndexKlineVO>> listKlines(@ModelAttribute IndexKlineParam param) {
        return ResponseEntity.ok(this.indexMarketQueryService.listKlines(param));
    }

    @PostMapping("/sync")
    public ApiResponseVO<MarketSyncStatusVO> syncIndices() {
        boolean started = this.indexMarketSyncTask.startManualSync();
        return ApiResponseVO.success(new MarketSyncStatusVO(started, this.indexMarketSyncTask.isSyncing()));
    }

    @PostMapping("/sync/klines/{indexCode}")
    public ApiResponseVO<MarketSyncStatusVO> syncIndexKlines(
            @PathVariable String indexCode,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) Integer limit) {
        boolean synced = this.indexMarketSyncTask.syncKlinesForIndex(
                indexCode,
                IndexMarketController.normalizePeriodType(periodType),
                limit);
        return ApiResponseVO.success(new MarketSyncStatusVO(synced, false));
    }

    @GetMapping("/sync/status")
    public ApiResponseVO<MarketSyncStatusVO> indexSyncStatus() {
        return ApiResponseVO.success(new MarketSyncStatusVO(false, this.indexMarketSyncTask.isSyncing()));
    }

    private static KlinePeriodTypeEnum normalizePeriodType(String value) {
        if (StrUtil.isBlank(value)) {
            return KlinePeriodTypeEnum.DAILY;
        }
        String code = value.trim();
        for (KlinePeriodTypeEnum item : KlinePeriodTypeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return KlinePeriodTypeEnum.DAILY;
    }
}
