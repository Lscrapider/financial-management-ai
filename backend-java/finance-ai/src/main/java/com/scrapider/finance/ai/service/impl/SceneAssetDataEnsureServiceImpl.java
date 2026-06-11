package com.scrapider.finance.ai.service.impl;
import cn.hutool.core.collection.CollUtil;
import com.scrapider.finance.ai.converter.StockSceneDataConverter;
import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.ai.domain.dto.StockSceneDataDTO;
import com.scrapider.finance.ai.service.SceneAssetDataEnsureService;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.provider.StockFundamentalProvider;
import com.scrapider.finance.provider.ConvertibleBondSceneDataProvider;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
@Service
public class SceneAssetDataEnsureServiceImpl implements SceneAssetDataEnsureService {
    private static final int VALUATION_LIMIT = 250;
    private static final int FINANCIAL_LIMIT = 10;
    private static final int DIVIDEND_LIMIT = 10;
    private static final int FINANCIAL_FRESH_DAYS = 7;
    private static final int DIVIDEND_FRESH_DAYS = 30;
    private static final int BOND_FRESH_DAYS = 7;
    private static final String SNAPSHOT_OVERLAY_SOURCE = "snapshot_overlay";
    private final StockFundamentalProvider stockFundamentalProvider;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ObjectProvider<ConvertibleBondSceneDataProvider> convertibleBondSceneDataProvider;

    public SceneAssetDataEnsureServiceImpl(
            StockFundamentalProvider stockFundamentalProvider,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ObjectProvider<ConvertibleBondSceneDataProvider> convertibleBondSceneDataProvider) {
        this.stockFundamentalProvider = stockFundamentalProvider;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondSceneDataProvider = convertibleBondSceneDataProvider;
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

    @Override
    public ConvertibleBondSceneDataDTO ensureBondSceneData(
            BondConfigPO bond,
            BondQuoteSnapshotPO quote,
            Integer valuationLimit,
            Integer shareLimit) {
        if (bond == null || bond.getBondCode() == null || bond.getBondCode().isBlank()) {
            return ConvertibleBondSceneDataDTO.empty();
        }
        ConvertibleBondBasicPO basic = this.ensureBasicFresh(bond);
        ConvertibleBondSharePO latestShare = this.ensureShareFresh(bond, shareLimit);
        List<ConvertibleBondDailyValuationPO> valuations =
                this.convertibleBondDailyValuationManage.listByBondCode(bond.getBondCode(), valuationLimit);
        return new ConvertibleBondSceneDataDTO(
                basic,
                latestShare,
                this.overlayLatestValuation(valuations, quote));
    }

    private ConvertibleBondBasicPO ensureBasicFresh(BondConfigPO bond) {
        ConvertibleBondBasicPO latest = this.convertibleBondBasicManage.latestByBondCode(bond.getBondCode());
        if (this.isBondFresh(latest == null ? null : latest.getSyncedAt())) {
            return latest;
        }
        ConvertibleBondSceneDataProvider provider = this.convertibleBondSceneDataProvider.getIfAvailable();
        if (provider == null) {
            return latest;
        }
        ConvertibleBondBasicPO refreshed = provider.getBasic(bond);
        if (refreshed != null) {
            this.convertibleBondBasicManage.saveBasic(refreshed);
            return refreshed;
        }
        return latest;
    }

    private ConvertibleBondSharePO ensureShareFresh(BondConfigPO bond, Integer shareLimit) {
        ConvertibleBondSharePO latest = this.convertibleBondShareManage.latestByBondCode(bond.getBondCode());
        if (this.isBondFresh(latest == null ? null : latest.getSyncedAt())) {
            return latest;
        }
        ConvertibleBondSceneDataProvider provider = this.convertibleBondSceneDataProvider.getIfAvailable();
        if (provider == null) {
            return latest;
        }
        List<ConvertibleBondSharePO> refreshed = provider.getShareChanges(bond, shareLimit);
        if (CollUtil.isNotEmpty(refreshed)) {
            this.convertibleBondShareManage.saveShares(refreshed);
            return refreshed.stream()
                    .filter(item -> item.getEndDate() != null)
                    .max(java.util.Comparator.comparing(ConvertibleBondSharePO::getEndDate))
                    .orElse(refreshed.get(0));
        }
        return latest;
    }

    private boolean isBondFresh(LocalDateTime syncedAt) {
        return syncedAt != null && !syncedAt.isBefore(LocalDateTime.now().minusDays(BOND_FRESH_DAYS));
    }

    private List<ConvertibleBondDailyValuationPO> overlayLatestValuation(
            List<ConvertibleBondDailyValuationPO> valuations,
            BondQuoteSnapshotPO quote) {
        if (CollUtil.isEmpty(valuations) || quote == null || quote.getSyncedAt() == null) {
            return valuations == null ? List.of() : valuations;
        }
        ConvertibleBondDailyValuationPO latest = valuations.get(0);
        if (latest.getSyncedAt() == null) {
            return valuations;
        }
        ConvertibleBondDailyValuationPO overlay = this.overlay(latest, quote);
        List<ConvertibleBondDailyValuationPO> result = new ArrayList<>();
        LocalDate latestSyncedDate = latest.getSyncedAt().toLocalDate();
        LocalDate quoteSyncedDate = quote.getSyncedAt().toLocalDate();
        if (!latestSyncedDate.equals(quoteSyncedDate)) {
            overlay.setTradeDate(quoteSyncedDate);
            result.add(overlay);
            result.addAll(valuations);
            return result;
        }
        result.add(overlay);
        result.addAll(valuations.subList(1, valuations.size()));
        return result;
    }

    private ConvertibleBondDailyValuationPO overlay(
            ConvertibleBondDailyValuationPO latest,
            BondQuoteSnapshotPO quote) {
        ConvertibleBondDailyValuationPO result = this.copy(latest);
        if (quote.getLatestPrice() != null) {
            result.setClosePrice(quote.getLatestPrice());
        }
        if (quote.getConversionValue() != null) {
            result.setConversionValue(quote.getConversionValue());
        }
        if (quote.getConversionPremiumRate() != null) {
            result.setPremiumRate(quote.getConversionPremiumRate());
        }
        if (quote.getVolume() != null) {
            result.setVolume(quote.getVolume());
        }
        if (quote.getTurnoverAmount() != null) {
            result.setTurnoverAmount(quote.getTurnoverAmount());
        }
        result.setSyncedAt(quote.getSyncedAt());
        result.setSource(SNAPSHOT_OVERLAY_SOURCE);
        return result;
    }

    private ConvertibleBondDailyValuationPO copy(ConvertibleBondDailyValuationPO source) {
        ConvertibleBondDailyValuationPO result = new ConvertibleBondDailyValuationPO();
        result.setBondCode(source.getBondCode());
        result.setBondName(source.getBondName());
        result.setTsCode(source.getTsCode());
        result.setTradeDate(source.getTradeDate());
        result.setClosePrice(source.getClosePrice());
        result.setConversionValue(source.getConversionValue());
        result.setPremiumRate(source.getPremiumRate());
        result.setPureBondValue(source.getPureBondValue());
        result.setPureBondPremiumRate(source.getPureBondPremiumRate());
        result.setYtm(source.getYtm());
        result.setVolume(source.getVolume());
        result.setTurnoverAmount(source.getTurnoverAmount());
        result.setSource(source.getSource());
        result.setRawResponse(source.getRawResponse());
        result.setSyncedAt(source.getSyncedAt());
        return result;
    }
}
