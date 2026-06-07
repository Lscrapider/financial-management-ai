package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.scrapider.finance.ai.converter.StockSceneDataConverter;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.service.StockFundamentalProvider;
import com.scrapider.finance.ai.service.StockSceneDataEnsureService;
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

    private final StockFundamentalProvider stockFundamentalProvider;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;

    public StockSceneDataEnsureServiceImpl(
            StockFundamentalProvider stockFundamentalProvider,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage) {
        this.stockFundamentalProvider = stockFundamentalProvider;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
    }

    @Override
    public StockSceneDataDTO ensureStockSceneData(StockConfigPO stockConfig) {
        if (stockConfig == null) {
            return StockSceneDataConverter.empty();
        }
        this.ensureIndustryInfoFresh(stockConfig);
        this.ensureValuationHistoryFresh(stockConfig);
        this.ensureIndustryInfoFromValuationHistory(stockConfig);
        this.ensureFinancialIndicatorsFresh(stockConfig);
        this.ensureDividendHistoryFresh(stockConfig);
        return StockSceneDataConverter.toDTO(
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
        List<StockValuationHistoryPO> valuations = this.stockFundamentalProvider.getValuationHistory(
                stockConfig,
                VALUATION_LIMIT);
        if (CollUtil.isNotEmpty(valuations)) {
            this.stockValuationHistoryManage.saveValuationHistory(valuations);
        }
    }

    private void ensureIndustryInfoFresh(StockConfigPO stockConfig) {
        StockIndustryInfoPO existing = this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid());
        if (this.isFreshWithinDays(existing == null ? null : existing.getSyncedAt(), FINANCIAL_FRESH_DAYS)) {
            return;
        }
        StockIndustryInfoPO industryInfo = this.stockFundamentalProvider.getIndustryInfo(stockConfig);
        if (industryInfo != null) {
            this.stockIndustryInfoManage.saveIndustryInfo(industryInfo);
        }
    }

    private void ensureIndustryInfoFromValuationHistory(StockConfigPO stockConfig) {
        StockIndustryInfoPO existing = this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid());
        if (existing != null && existing.getIndustryName() != null && !existing.getIndustryName().isBlank()) {
            return;
        }
        StockValuationHistoryPO latest = this.stockValuationHistoryManage.latestByStockCode(stockConfig.getStockCode());
        if (latest == null || latest.getBoardName() == null || latest.getBoardName().isBlank()) {
            return;
        }
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
        List<StockFinancialIndicatorPO> indicators = this.stockFundamentalProvider.getFinancialIndicators(
                stockConfig,
                FINANCIAL_LIMIT);
        if (CollUtil.isNotEmpty(indicators)) {
            this.stockFinancialIndicatorManage.saveFinancialIndicators(indicators);
        }
    }

    private void ensureDividendHistoryFresh(StockConfigPO stockConfig) {
        StockDividendHistoryPO latest = this.stockDividendHistoryManage.latestByStockCode(stockConfig.getStockCode());
        if (this.isFreshWithinDays(latest == null ? null : latest.getSyncedAt(), DIVIDEND_FRESH_DAYS)) {
            return;
        }
        List<StockDividendHistoryPO> dividends = this.stockFundamentalProvider.getDividendHistory(
                stockConfig,
                DIVIDEND_LIMIT);
        if (CollUtil.isNotEmpty(dividends)) {
            this.stockDividendHistoryManage.saveDividendHistory(dividends);
        }
    }

    private boolean isFreshWithinDays(LocalDateTime syncedAt, int days) {
        return syncedAt != null && !syncedAt.isBefore(LocalDateTime.now().minusDays(days));
    }

}
