package com.scrapider.finance.service.impl;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import com.scrapider.finance.service.AssetDataInitializationService;
import com.scrapider.finance.task.BondMarketSyncTask;
import com.scrapider.finance.task.StockMarketSyncTask;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 将新增标的后的慢数据初始化放入后台，避免阻塞配置接口响应。
 */
@Slf4j
@Service
public class AssetDataInitializationServiceImpl implements AssetDataInitializationService {

    private final StockMarketSyncTask stockMarketSyncTask;
    private final BondMarketSyncTask bondMarketSyncTask;
    private final AssetDataEnsureService assetDataEnsureService;

    public AssetDataInitializationServiceImpl(
            StockMarketSyncTask stockMarketSyncTask,
            BondMarketSyncTask bondMarketSyncTask,
            AssetDataEnsureService assetDataEnsureService) {
        this.stockMarketSyncTask = stockMarketSyncTask;
        this.bondMarketSyncTask = bondMarketSyncTask;
        this.assetDataEnsureService = assetDataEnsureService;
    }

    @Override
    public boolean scheduleStockInitialization(StockConfigPO stock) {
        if (stock == null || this.isBlank(stock.getStockCode())) {
            return false;
        }
        try {
            CompletableFuture.runAsync(() -> this.initializeStock(stock));
            return true;
        } catch (RuntimeException ex) {
            log.warn("投递股票后台初始化失败，stockCode: {}", stock.getStockCode(), ex);
            return false;
        }
    }

    @Override
    public boolean scheduleConvertibleBondInitialization(BondConfigPO bond) {
        if (bond == null || this.isBlank(bond.getBondCode())) {
            return false;
        }
        try {
            CompletableFuture.runAsync(() -> this.initializeConvertibleBond(bond));
            return true;
        } catch (RuntimeException ex) {
            log.warn("投递可转债后台初始化失败，bondCode: {}", bond.getBondCode(), ex);
            return false;
        }
    }

    private void initializeStock(StockConfigPO stock) {
        try {
            boolean trendSynced = this.stockMarketSyncTask.syncStockTrend(stock.getStockCode());
            log.info("股票后台分时初始化完成，stockCode: {}, success: {}", stock.getStockCode(), trendSynced);
        } catch (Exception ex) {
            log.warn("股票后台分时初始化失败，stockCode: {}", stock.getStockCode(), ex);
        }
        try {
            AssetDataEnsureResult result = this.assetDataEnsureService.ensureStockData(stock);
            log.info(
                    "股票后台基础数据初始化完成，stockCode: {}, unavailableSections: {}",
                    stock.getStockCode(),
                    result.unavailableSections());
        } catch (Exception ex) {
            log.warn("股票后台基础数据初始化失败，stockCode: {}", stock.getStockCode(), ex);
        }
    }

    private void initializeConvertibleBond(BondConfigPO bond) {
        try {
            boolean marketDataSynced = this.bondMarketSyncTask.syncMarketDataForBond(bond.getBondCode());
            log.info("可转债后台行情初始化完成，bondCode: {}, success: {}", bond.getBondCode(), marketDataSynced);
        } catch (Exception ex) {
            log.warn("可转债后台行情初始化失败，bondCode: {}", bond.getBondCode(), ex);
        }
        try {
            AssetDataEnsureResult result = this.assetDataEnsureService.ensureConvertibleBondData(bond);
            log.info(
                    "可转债后台基础数据初始化完成，bondCode: {}, unavailableSections: {}",
                    bond.getBondCode(),
                    result.unavailableSections());
        } catch (Exception ex) {
            log.warn("可转债后台基础数据初始化失败，bondCode: {}", bond.getBondCode(), ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
