package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.ai.service.ConvertibleBondSceneDataEnsureService;
import com.scrapider.finance.ai.service.ConvertibleBondSceneDataProvider;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ConvertibleBondSceneDataEnsureServiceImpl implements ConvertibleBondSceneDataEnsureService {

    private static final int FRESH_DAYS = 7;
    private static final String SNAPSHOT_OVERLAY_SOURCE = "snapshot_overlay";

    private final ConvertibleBondBasicManage convertibleBondBasicManage;
    private final ConvertibleBondShareManage convertibleBondShareManage;
    private final ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage;
    private final ObjectProvider<ConvertibleBondSceneDataProvider> convertibleBondSceneDataProvider;

    public ConvertibleBondSceneDataEnsureServiceImpl(
            ConvertibleBondBasicManage convertibleBondBasicManage,
            ConvertibleBondShareManage convertibleBondShareManage,
            ConvertibleBondDailyValuationManage convertibleBondDailyValuationManage,
            ObjectProvider<ConvertibleBondSceneDataProvider> convertibleBondSceneDataProvider) {
        this.convertibleBondBasicManage = convertibleBondBasicManage;
        this.convertibleBondShareManage = convertibleBondShareManage;
        this.convertibleBondDailyValuationManage = convertibleBondDailyValuationManage;
        this.convertibleBondSceneDataProvider = convertibleBondSceneDataProvider;
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
        if (this.isFresh(latest == null ? null : latest.getSyncedAt())) {
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
        if (this.isFresh(latest == null ? null : latest.getSyncedAt())) {
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

    private boolean isFresh(LocalDateTime syncedAt) {
        return syncedAt != null && !syncedAt.isBefore(LocalDateTime.now().minusDays(FRESH_DAYS));
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
