package com.scrapider.finance.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockIndustryInfoManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import com.scrapider.finance.provider.ConvertibleBondDataProvider;
import com.scrapider.finance.provider.StockFundamentalProvider;
import com.scrapider.finance.service.AssetDataEnsurePolicy;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 复用既有数据源与落库规则，确保 Agent、报告和后台初始化读取同一份标的数据。
 */
@Service
public class AssetDataEnsureServiceImpl implements AssetDataEnsureService {

    private static final String INDUSTRY_SECTION = "industry";
    private static final String VALUATION_SECTION = "valuation";
    private static final String FINANCIAL_SECTION = "financialIndicators";
    private static final String DIVIDEND_SECTION = "dividends";
    private static final String BASIC_SECTION = "basic";
    private static final String DAILY_VALUATION_SECTION = "dailyValuation";
    private static final String SHARE_SECTION = "shareChanges";

    private final StockFundamentalProvider stockFundamentalProvider;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider;
    private final Integer convertibleDailyLimit;
    private final ConcurrentMap<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    public AssetDataEnsureServiceImpl(
            StockFundamentalProvider stockFundamentalProvider,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ObjectProvider<ConvertibleBondDataProvider> convertibleBondDataProvider,
            @Value("${bond.sync.convertible-daily-limit}") Integer convertibleDailyLimit) {
        this.stockFundamentalProvider = stockFundamentalProvider;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondDataProvider = convertibleBondDataProvider;
        this.convertibleDailyLimit = convertibleDailyLimit;
    }

    @Override
    public AssetDataEnsureResult ensureStockData(StockConfigPO stock) {
        if (!this.hasStockIdentity(stock)) {
            return new AssetDataEnsureResult(false, List.of(
                    INDUSTRY_SECTION,
                    VALUATION_SECTION,
                    FINANCIAL_SECTION,
                    DIVIDEND_SECTION));
        }
        return this.executeExclusively("stock:" + stock.getStockCode(), () -> this.doEnsureStockData(stock));
    }

    @Override
    public AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond) {
        if (!this.hasBondCode(bond)) {
            return new AssetDataEnsureResult(false, List.of(
                    BASIC_SECTION,
                    DAILY_VALUATION_SECTION,
                    SHARE_SECTION));
        }
        return this.executeExclusively("bond:" + bond.getBondCode(), () -> this.doEnsureConvertibleBondData(bond));
    }

    @Override
    public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
        if (!this.hasBondCode(bond)) {
            return false;
        }
        return this.executeExclusively("bond:" + bond.getBondCode(), () -> {
            this.refreshConvertibleBondDailyValuations(
                    bond,
                    this.convertibleBondDataProvider.getIfAvailable());
            return this.convertibleBondDailyValuationManage.latestByBondCode(bond.getBondCode()) != null;
        });
    }

    private AssetDataEnsureResult doEnsureStockData(StockConfigPO stock) {
        boolean refreshAttempted = this.refreshIndustryInfo(stock);
        refreshAttempted |= this.refreshValuationHistory(stock);
        refreshAttempted |= this.backfillIndustryInfoFromValuationHistory(stock);
        refreshAttempted |= this.refreshFinancialIndicators(stock);
        refreshAttempted |= this.refreshDividendHistory(stock);

        List<String> unavailableSections = new ArrayList<>();
        StockIndustryInfoPO industry = this.stockIndustryInfoManage.getBySecid(stock.getSecid());
        if (industry == null || this.isBlank(industry.getIndustryName())) {
            unavailableSections.add(INDUSTRY_SECTION);
        }
        StockValuationHistoryPO valuation = this.stockValuationHistoryManage.latestByStockCode(stock.getStockCode());
        if (!this.isValuationFresh(valuation == null ? null : valuation.getSyncedAt())) {
            unavailableSections.add(VALUATION_SECTION);
        }
        StockFinancialIndicatorPO financial =
                this.stockFinancialIndicatorManage.latestByStockCode(stock.getStockCode());
        if (!this.isFreshWithinDays(
                financial == null ? null : financial.getSyncedAt(),
                AssetDataEnsurePolicy.STOCK_FUNDAMENTAL_FRESH_DAYS)) {
            unavailableSections.add(FINANCIAL_SECTION);
        }
        StockDividendHistoryPO dividend = this.stockDividendHistoryManage.latestByStockCode(stock.getStockCode());
        if (!this.isFreshWithinDays(
                dividend == null ? null : dividend.getSyncedAt(),
                AssetDataEnsurePolicy.STOCK_DIVIDEND_FRESH_DAYS)) {
            unavailableSections.add(DIVIDEND_SECTION);
        }
        return new AssetDataEnsureResult(refreshAttempted, unavailableSections);
    }

    private boolean refreshIndustryInfo(StockConfigPO stock) {
        StockIndustryInfoPO existing = this.stockIndustryInfoManage.getBySecid(stock.getSecid());
        if (this.isFreshWithinDays(
                existing == null ? null : existing.getSyncedAt(),
                AssetDataEnsurePolicy.STOCK_FUNDAMENTAL_FRESH_DAYS)) {
            return false;
        }
        StockIndustryInfoPO industry = this.stockFundamentalProvider.getIndustryInfo(stock);
        if (industry != null) {
            this.stockIndustryInfoManage.saveIndustryInfo(industry);
        }
        return true;
    }

    private boolean refreshValuationHistory(StockConfigPO stock) {
        StockValuationHistoryPO latest = this.stockValuationHistoryManage.latestByStockCode(stock.getStockCode());
        if (this.isValuationFresh(latest == null ? null : latest.getSyncedAt())) {
            return false;
        }
        List<StockValuationHistoryPO> valuations = this.stockFundamentalProvider.getValuationHistory(
                stock,
                AssetDataEnsurePolicy.STOCK_VALUATION_LIMIT);
        if (CollUtil.isNotEmpty(valuations)) {
            this.stockValuationHistoryManage.saveValuationHistory(valuations);
        }
        return true;
    }

    private boolean backfillIndustryInfoFromValuationHistory(StockConfigPO stock) {
        StockIndustryInfoPO existing = this.stockIndustryInfoManage.getBySecid(stock.getSecid());
        if (existing != null && !this.isBlank(existing.getIndustryName())) {
            return false;
        }
        StockValuationHistoryPO latest = this.stockValuationHistoryManage.latestByStockCode(stock.getStockCode());
        if (latest == null || this.isBlank(latest.getBoardName())) {
            return false;
        }
        this.stockIndustryInfoManage.saveIndustryInfo(StockIndustryInfoPO.fromValuationHistory(stock, latest));
        return true;
    }

    private boolean refreshFinancialIndicators(StockConfigPO stock) {
        StockFinancialIndicatorPO latest =
                this.stockFinancialIndicatorManage.latestByStockCode(stock.getStockCode());
        if (this.isFreshWithinDays(
                latest == null ? null : latest.getSyncedAt(),
                AssetDataEnsurePolicy.STOCK_FUNDAMENTAL_FRESH_DAYS)) {
            return false;
        }
        List<StockFinancialIndicatorPO> financialIndicators = this.stockFundamentalProvider.getFinancialIndicators(
                stock,
                AssetDataEnsurePolicy.STOCK_FINANCIAL_LIMIT);
        if (CollUtil.isNotEmpty(financialIndicators)) {
            this.stockFinancialIndicatorManage.saveFinancialIndicators(financialIndicators);
        }
        return true;
    }

    private boolean refreshDividendHistory(StockConfigPO stock) {
        StockDividendHistoryPO latest = this.stockDividendHistoryManage.latestByStockCode(stock.getStockCode());
        if (this.isFreshWithinDays(
                latest == null ? null : latest.getSyncedAt(),
                AssetDataEnsurePolicy.STOCK_DIVIDEND_FRESH_DAYS)) {
            return false;
        }
        List<StockDividendHistoryPO> dividends = this.stockFundamentalProvider.getDividendHistory(
                stock,
                AssetDataEnsurePolicy.STOCK_DIVIDEND_LIMIT);
        if (CollUtil.isNotEmpty(dividends)) {
            this.stockDividendHistoryManage.saveDividendHistory(dividends);
        }
        return true;
    }

    private AssetDataEnsureResult doEnsureConvertibleBondData(BondConfigPO bond) {
        ConvertibleBondDataProvider provider = this.convertibleBondDataProvider.getIfAvailable();
        boolean refreshAttempted = this.refreshConvertibleBondBasic(bond, provider);
        refreshAttempted |= this.refreshConvertibleBondDailyValuations(bond, provider);
        refreshAttempted |= this.refreshConvertibleBondShares(bond, provider);

        List<String> unavailableSections = new ArrayList<>();
        ConvertibleBondBasicPO basic = this.convertibleBondBasicManage.latestByBondCode(bond.getBondCode());
        if (!this.isFreshWithinDays(
                basic == null ? null : basic.getSyncedAt(),
                AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)) {
            unavailableSections.add(BASIC_SECTION);
        }
        if (this.convertibleBondDailyValuationManage.latestByBondCode(bond.getBondCode()) == null) {
            unavailableSections.add(DAILY_VALUATION_SECTION);
        }
        ConvertibleBondSharePO share = this.convertibleBondShareManage.latestByBondCode(bond.getBondCode());
        if (!this.isFreshWithinDays(
                share == null ? null : share.getSyncedAt(),
                AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)) {
            unavailableSections.add(SHARE_SECTION);
        }
        return new AssetDataEnsureResult(refreshAttempted, unavailableSections);
    }

    private boolean refreshConvertibleBondBasic(BondConfigPO bond, ConvertibleBondDataProvider provider) {
        ConvertibleBondBasicPO latest = this.convertibleBondBasicManage.latestByBondCode(bond.getBondCode());
        if (this.isFreshWithinDays(
                latest == null ? null : latest.getSyncedAt(),
                AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)) {
            return false;
        }
        if (provider == null) {
            return false;
        }
        ConvertibleBondBasicPO basic = provider.getBasic(bond);
        if (basic != null) {
            this.convertibleBondBasicManage.saveBasic(basic);
        }
        return true;
    }

    private boolean refreshConvertibleBondDailyValuations(BondConfigPO bond, ConvertibleBondDataProvider provider) {
        if (this.convertibleBondDailyValuationManage.latestByBondCode(bond.getBondCode()) != null) {
            return false;
        }
        if (provider == null) {
            return false;
        }
        List<ConvertibleBondDailyValuationPO> valuations =
                provider.getDailyValuations(bond, this.convertibleDailyLimit);
        if (CollUtil.isNotEmpty(valuations)) {
            this.convertibleBondDailyValuationManage.saveValuations(valuations);
        }
        return true;
    }

    private boolean refreshConvertibleBondShares(BondConfigPO bond, ConvertibleBondDataProvider provider) {
        ConvertibleBondSharePO latest = this.convertibleBondShareManage.latestByBondCode(bond.getBondCode());
        if (this.isFreshWithinDays(
                latest == null ? null : latest.getSyncedAt(),
                AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)) {
            return false;
        }
        if (provider == null) {
            return false;
        }
        List<ConvertibleBondSharePO> shares = provider.getShareChanges(bond, this.convertibleDailyLimit);
        if (CollUtil.isNotEmpty(shares)) {
            this.convertibleBondShareManage.saveShares(shares);
        }
        return true;
    }

    private boolean hasStockIdentity(StockConfigPO stock) {
        return stock != null && !this.isBlank(stock.getStockCode()) && !this.isBlank(stock.getSecid());
    }

    private boolean hasBondCode(BondConfigPO bond) {
        return bond != null && !this.isBlank(bond.getBondCode());
    }

    private boolean isValuationFresh(LocalDateTime syncedAt) {
        return syncedAt != null && LocalDate.now().equals(syncedAt.toLocalDate());
    }

    private boolean isFreshWithinDays(LocalDateTime syncedAt, int days) {
        return syncedAt != null && !syncedAt.isBefore(LocalDateTime.now().minusDays(days));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T executeExclusively(String key, Supplier<T> action) {
        while (true) {
            CompletableFuture<Void> created = new CompletableFuture<>();
            CompletableFuture<Void> existing = this.inFlight.putIfAbsent(key, created);
            if (existing != null) {
                existing.join();
                continue;
            }
            try {
                return action.get();
            } finally {
                created.complete(null);
                this.inFlight.remove(key, created);
            }
        }
    }
}
