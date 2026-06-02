package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.scrapider.finance.ai.api.EastMoneyDividendApi;
import com.scrapider.finance.ai.api.EastMoneyFinanceApi;
import com.scrapider.finance.ai.api.EastMoneyValuationApi;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.service.StockSceneDataEnsureService;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockIndustryInfoManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockSceneDataEnsureServiceImpl implements StockSceneDataEnsureService {

    private static final int VALUATION_LIMIT = 250;
    private static final int FINANCIAL_LIMIT = 10;
    private static final int DIVIDEND_LIMIT = 10;
    private static final int FINANCIAL_FRESH_DAYS = 7;
    private static final int DIVIDEND_FRESH_DAYS = 30;

    private final EastMoneyValuationApi eastMoneyValuationApi;
    private final EastMoneyFinanceApi eastMoneyFinanceApi;
    private final EastMoneyDividendApi eastMoneyDividendApi;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;

    public StockSceneDataEnsureServiceImpl(
            EastMoneyValuationApi eastMoneyValuationApi,
            EastMoneyFinanceApi eastMoneyFinanceApi,
            EastMoneyDividendApi eastMoneyDividendApi,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage) {
        this.eastMoneyValuationApi = eastMoneyValuationApi;
        this.eastMoneyFinanceApi = eastMoneyFinanceApi;
        this.eastMoneyDividendApi = eastMoneyDividendApi;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
    }

    @Override
    public StockSceneDataDTO ensureStockSceneData(StockConfigPO stockConfig) {
        if (stockConfig == null) {
            return new StockSceneDataDTO(null, List.of(), List.of(), List.of());
        }
        this.ensureValuationHistoryFresh(stockConfig);
        this.ensureIndustryInfoFromValuationHistory(stockConfig);
        this.ensureFinancialIndicatorsFresh(stockConfig);
        this.ensureDividendHistoryFresh(stockConfig);
        return new StockSceneDataDTO(
                this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid()),
                this.stockValuationHistoryManage.listByStockCode(stockConfig.getStockCode(), VALUATION_LIMIT),
                this.stockFinancialIndicatorManage.listByStockCode(stockConfig.getStockCode(), FINANCIAL_LIMIT),
                this.stockDividendHistoryManage.listByStockCode(stockConfig.getStockCode(), DIVIDEND_LIMIT));
    }

    private void ensureValuationHistoryFresh(StockConfigPO stockConfig) {
        StockValuationHistoryPO latest = this.stockValuationHistoryManage.latestByStockCode(stockConfig.getStockCode());
        if (latest != null && latest.getSyncedAt() != null && latest.getSyncedAt().toLocalDate().equals(LocalDate.now())) {
            return;
        }
        StockMarketDataDTO response = this.eastMoneyValuationApi.getValuationHistory(
                stockConfig.getStockCode(),
                VALUATION_LIMIT);
        List<StockValuationHistoryPO> valuations = StockValuationHistoryPO.fromEastMoneyResponse(
                stockConfig,
                response.data());
        if (CollUtil.isNotEmpty(valuations)) {
            this.stockValuationHistoryManage.saveValuationHistory(valuations);
        }
    }

    private void ensureIndustryInfoFromValuationHistory(StockConfigPO stockConfig) {
        StockValuationHistoryPO latest = this.stockValuationHistoryManage.latestByStockCode(stockConfig.getStockCode());
        if (latest == null || latest.getBoardName() == null || latest.getBoardName().isBlank()) {
            return;
        }
        StockIndustryInfoPO existing = this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid());
        if (existing != null && latest.getBoardName().equals(existing.getIndustryName())) {
            return;
        }
        this.stockIndustryInfoManage.saveIndustryInfo(StockIndustryInfoPO.fromValuationHistory(stockConfig, latest));
    }

    private void ensureFinancialIndicatorsFresh(StockConfigPO stockConfig) {
        StockFinancialIndicatorPO latest =
                this.stockFinancialIndicatorManage.latestByStockCode(stockConfig.getStockCode());
        if (this.isFreshWithinDays(latest == null ? null : latest.getSyncedAt(), FINANCIAL_FRESH_DAYS)) {
            return;
        }
        StockMarketDataDTO response = this.eastMoneyFinanceApi.getMainFinancialIndicators(
                this.toSecucode(stockConfig),
                FINANCIAL_LIMIT);
        List<StockFinancialIndicatorPO> indicators = StockFinancialIndicatorPO.fromEastMoneyResponse(
                stockConfig,
                response.data());
        if (CollUtil.isNotEmpty(indicators)) {
            this.stockFinancialIndicatorManage.saveFinancialIndicators(indicators);
        }
    }

    private void ensureDividendHistoryFresh(StockConfigPO stockConfig) {
        StockDividendHistoryPO latest = this.stockDividendHistoryManage.latestByStockCode(stockConfig.getStockCode());
        if (this.isFreshWithinDays(latest == null ? null : latest.getSyncedAt(), DIVIDEND_FRESH_DAYS)) {
            return;
        }
        StockMarketDataDTO response = this.eastMoneyDividendApi.getDividendHistory(
                stockConfig.getStockCode(),
                DIVIDEND_LIMIT);
        List<StockDividendHistoryPO> dividends = StockDividendHistoryPO.fromEastMoneyResponse(
                stockConfig,
                response.data());
        if (CollUtil.isNotEmpty(dividends)) {
            this.stockDividendHistoryManage.saveDividendHistory(dividends);
        }
    }

    private boolean isFreshWithinDays(LocalDateTime syncedAt, int days) {
        return syncedAt != null && !syncedAt.isBefore(LocalDateTime.now().minusDays(days));
    }

    private String toSecucode(StockConfigPO stockConfig) {
        String[] parts = stockConfig.getSecid().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid secid: " + stockConfig.getSecid());
        }
        return "%s.%s".formatted(stockConfig.getStockCode(), "1".equals(parts[0]) ? "SH" : "SZ");
    }
}
