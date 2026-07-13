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
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockIndustryInfoManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import com.scrapider.finance.service.AssetDataEnsurePolicy;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 为 Report 组装场景 DTO，并委托共享服务补齐本地数据。
 */
@Service
public class SceneAssetDataEnsureServiceImpl implements SceneAssetDataEnsureService {

    private static final String SNAPSHOT_OVERLAY_SOURCE = "snapshot_overlay";

    private final AssetDataEnsureService assetDataEnsureService;
    private final StockIndustryInfoManage stockIndustryInfoManage;
    private final StockValuationHistoryManage stockValuationHistoryManage;
    private final StockFinancialIndicatorManage stockFinancialIndicatorManage;
    private final StockDividendHistoryManage stockDividendHistoryManage;
    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;

    public SceneAssetDataEnsureServiceImpl(
            AssetDataEnsureService assetDataEnsureService,
            StockIndustryInfoManage stockIndustryInfoManage,
            StockValuationHistoryManage stockValuationHistoryManage,
            StockFinancialIndicatorManage stockFinancialIndicatorManage,
            StockDividendHistoryManage stockDividendHistoryManage,
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage) {
        this.assetDataEnsureService = assetDataEnsureService;
        this.stockIndustryInfoManage = stockIndustryInfoManage;
        this.stockValuationHistoryManage = stockValuationHistoryManage;
        this.stockFinancialIndicatorManage = stockFinancialIndicatorManage;
        this.stockDividendHistoryManage = stockDividendHistoryManage;
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
    }

    @Override
    public StockSceneDataDTO ensureStockSceneData(StockConfigPO stockConfig) {
        if (stockConfig == null) {
            return StockSceneDataConverter.empty();
        }
        this.assetDataEnsureService.ensureStockData(stockConfig);
        return StockSceneDataConverter.toDTO(
                this.stockIndustryInfoManage.getBySecid(stockConfig.getSecid()),
                this.stockValuationHistoryManage.listByStockCode(
                        stockConfig.getStockCode(),
                        AssetDataEnsurePolicy.STOCK_VALUATION_LIMIT),
                this.stockFinancialIndicatorManage.listByStockCode(
                        stockConfig.getStockCode(),
                        AssetDataEnsurePolicy.STOCK_FINANCIAL_LIMIT),
                this.stockDividendHistoryManage.listByStockCode(
                        stockConfig.getStockCode(),
                        AssetDataEnsurePolicy.STOCK_DIVIDEND_LIMIT));
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
        this.assetDataEnsureService.ensureConvertibleBondData(bond);
        ConvertibleBondBasicPO basic = this.convertibleBondBasicManage.latestByBondCode(bond.getBondCode());
        ConvertibleBondSharePO latestShare = this.convertibleBondShareManage.latestByBondCode(bond.getBondCode());
        List<ConvertibleBondDailyValuationPO> valuations =
                this.convertibleBondDailyValuationManage.listByBondCode(bond.getBondCode(), valuationLimit);
        return new ConvertibleBondSceneDataDTO(
                basic,
                latestShare,
                this.overlayLatestValuation(valuations, quote));
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
