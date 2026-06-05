package com.scrapider.finance.controller;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.param.BondKlineParam;
import com.scrapider.finance.domain.param.BondIntradayTrendParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.vo.ApiResponseVO;
import com.scrapider.finance.domain.vo.BondKlineVO;
import com.scrapider.finance.domain.vo.BondIntradayTrendVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import com.scrapider.finance.domain.vo.MarketSyncStatusVO;
import com.scrapider.finance.service.BondMarketQueryService;
import com.scrapider.finance.task.BondMarketSyncTask;
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

    @GetMapping("/intraday-trends")
    public ResponseEntity<List<BondIntradayTrendVO>> listIntradayTrends(
            @ModelAttribute BondIntradayTrendParam param) {
        return ResponseEntity.ok(this.bondMarketQueryService.listIntradayTrends(param));
    }

    @GetMapping("/klines")
    public ResponseEntity<List<BondKlineVO>> listKlines(@ModelAttribute BondKlineParam param) {
        return ResponseEntity.ok(this.bondMarketQueryService.listKlines(param));
    }

    @PostMapping("/sync")
    public ApiResponseVO<MarketSyncStatusVO> syncBonds() {
        boolean started = this.bondMarketSyncTask.startManualSync();
        return ApiResponseVO.success(new MarketSyncStatusVO(started, this.bondMarketSyncTask.isSyncing()));
    }

    @PostMapping("/sync/klines/{bondCode}")
    public ApiResponseVO<MarketSyncStatusVO> syncBondKlines(
            @PathVariable String bondCode,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) Integer limit) {
        boolean synced = this.bondMarketSyncTask.syncKlinesForBond(
                bondCode,
                normalizePeriodType(periodType),
                limit);
        return ApiResponseVO.success(new MarketSyncStatusVO(synced, false));
    }

    @GetMapping("/sync/status")
    public ApiResponseVO<MarketSyncStatusVO> bondSyncStatus() {
        return ApiResponseVO.success(new MarketSyncStatusVO(false, this.bondMarketSyncTask.isSyncing()));
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
