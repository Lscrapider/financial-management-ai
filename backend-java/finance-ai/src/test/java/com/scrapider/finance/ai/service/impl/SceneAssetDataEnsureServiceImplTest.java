package com.scrapider.finance.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneAssetDataEnsureServiceImplTest {

    @Test
    void ensureReusesFreshBasicAndShareAndOverlaysLatestDailyWhenSyncedOnSameDate() {
        FakeBasicManage basicManage = new FakeBasicManage();
        FakeShareManage shareManage = new FakeShareManage();
        FakeValuationManage valuationManage = new FakeValuationManage();
        FakeAssetDataEnsureService assetDataEnsureService = new FakeAssetDataEnsureService();
        SceneAssetDataEnsureServiceImpl service = new SceneAssetDataEnsureServiceImpl(
                assetDataEnsureService,
                null,
                null,
                null,
                null,
                basicManage,
                shareManage,
                valuationManage);
        BondConfigPO bond = bond();
        ConvertibleBondBasicPO basic = basic(LocalDateTime.now().minusDays(1));
        ConvertibleBondSharePO share = share(LocalDateTime.now().minusDays(1));
        ConvertibleBondDailyValuationPO latestDaily = valuation(
                LocalDate.of(2026, 6, 5),
                LocalDateTime.of(2026, 6, 5, 18, 0),
                "118.20",
                "92.10",
                "28.34");
        BondQuoteSnapshotPO quote = quote(LocalDateTime.of(2026, 6, 5, 10, 30));

        basicManage.latest = basic;
        shareManage.latest = share;
        valuationManage.valuations = List.of(latestDaily);

        ConvertibleBondSceneDataDTO result = service.ensureBondSceneData(bond, quote, 250, 250);

        assertThat(result.valuationHistory()).hasSize(1);
        ConvertibleBondDailyValuationPO overlay = result.valuationHistory().get(0);
        assertThat(overlay.getTradeDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(overlay.getClosePrice()).isEqualByComparingTo("120.00");
        assertThat(overlay.getConversionValue()).isEqualByComparingTo("94.00");
        assertThat(overlay.getPremiumRate()).isEqualByComparingTo("27.66");
        assertThat(overlay.getPureBondValue()).isEqualByComparingTo("96.50");
        assertThat(assetDataEnsureService.bondEnsureCalls).isEqualTo(1);
    }

    @Test
    void ensureCreatesSyntheticDailyWhenSnapshotSyncedOnLaterDate() {
        FakeBasicManage basicManage = new FakeBasicManage();
        FakeShareManage shareManage = new FakeShareManage();
        FakeValuationManage valuationManage = new FakeValuationManage();
        FakeAssetDataEnsureService assetDataEnsureService = new FakeAssetDataEnsureService();
        SceneAssetDataEnsureServiceImpl service = new SceneAssetDataEnsureServiceImpl(
                assetDataEnsureService,
                null,
                null,
                null,
                null,
                basicManage,
                shareManage,
                valuationManage);
        BondConfigPO bond = bond();
        ConvertibleBondBasicPO basic = basic(LocalDateTime.now().minusDays(1));
        ConvertibleBondSharePO share = share(LocalDateTime.now().minusDays(1));
        ConvertibleBondDailyValuationPO latestDaily = valuation(
                LocalDate.of(2026, 6, 5),
                LocalDateTime.of(2026, 6, 5, 18, 0),
                "118.20",
                "92.10",
                "28.34");
        BondQuoteSnapshotPO quote = quote(LocalDateTime.of(2026, 6, 8, 10, 30));

        basicManage.latest = basic;
        shareManage.latest = share;
        valuationManage.valuations = List.of(latestDaily);

        ConvertibleBondSceneDataDTO result = service.ensureBondSceneData(bond, quote, 250, 250);

        assertThat(result.valuationHistory()).hasSize(2);
        ConvertibleBondDailyValuationPO synthetic = result.valuationHistory().get(0);
        assertThat(synthetic.getTradeDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(synthetic.getClosePrice()).isEqualByComparingTo("120.00");
        assertThat(synthetic.getConversionValue()).isEqualByComparingTo("94.00");
        assertThat(synthetic.getPremiumRate()).isEqualByComparingTo("27.66");
        assertThat(synthetic.getPureBondValue()).isEqualByComparingTo("96.50");
        assertThat(synthetic.getSource()).isEqualTo("snapshot_overlay");
        assertThat(result.valuationHistory().get(1).getTradeDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(assetDataEnsureService.bondEnsureCalls).isEqualTo(1);
    }

    @Test
    void ensureRefreshesBasicAndShareWhenSyncedMoreThanSevenDaysAgo() {
        FakeBasicManage basicManage = new FakeBasicManage();
        FakeShareManage shareManage = new FakeShareManage();
        FakeValuationManage valuationManage = new FakeValuationManage();
        FakeAssetDataEnsureService assetDataEnsureService = new FakeAssetDataEnsureService();
        SceneAssetDataEnsureServiceImpl service = new SceneAssetDataEnsureServiceImpl(
                assetDataEnsureService,
                null,
                null,
                null,
                null,
                basicManage,
                shareManage,
                valuationManage);
        BondConfigPO bond = bond();
        ConvertibleBondBasicPO staleBasic = basic(LocalDateTime.now().minusDays(8));
        ConvertibleBondSharePO staleShare = share(LocalDateTime.now().minusDays(8));
        ConvertibleBondBasicPO refreshedBasic = basic(LocalDateTime.now());
        ConvertibleBondSharePO refreshedShare = share(LocalDateTime.now());

        basicManage.latest = staleBasic;
        shareManage.latest = staleShare;
        assetDataEnsureService.bondEnsureAction = () -> {
            basicManage.saveBasic(refreshedBasic);
            shareManage.saveShares(List.of(refreshedShare));
        };
        valuationManage.valuations = List.of();

        ConvertibleBondSceneDataDTO result = service.ensureBondSceneData(bond, null, 250, 250);

        assertThat(result.basic()).isSameAs(refreshedBasic);
        assertThat(result.latestShare()).isSameAs(refreshedShare);
        assertThat(basicManage.saved).isSameAs(refreshedBasic);
        assertThat(shareManage.saved).containsExactly(refreshedShare);
    }

    @Test
    void ensureLoadsDailyValuationPersistedBySharedService() {
        FakeBasicManage basicManage = new FakeBasicManage();
        FakeShareManage shareManage = new FakeShareManage();
        FakeValuationManage valuationManage = new FakeValuationManage();
        FakeAssetDataEnsureService assetDataEnsureService = new FakeAssetDataEnsureService();
        SceneAssetDataEnsureServiceImpl service = new SceneAssetDataEnsureServiceImpl(
                assetDataEnsureService,
                null,
                null,
                null,
                null,
                basicManage,
                shareManage,
                valuationManage);
        ConvertibleBondDailyValuationPO daily = valuation(
                LocalDate.of(2026, 6, 8),
                LocalDateTime.of(2026, 6, 8, 18, 0),
                "118.20",
                "92.10",
                "28.34");
        assetDataEnsureService.bondEnsureAction = () -> valuationManage.valuations = List.of(daily);

        ConvertibleBondSceneDataDTO result = service.ensureBondSceneData(bond(), null, 250, 250);

        assertThat(assetDataEnsureService.bondEnsureCalls).isEqualTo(1);
        assertThat(result.valuationHistory()).containsExactly(daily);
    }

    private static class FakeBasicManage extends ConvertibleBondBasicManage {
        private ConvertibleBondBasicPO latest;
        private ConvertibleBondBasicPO saved;

        @Override
        public ConvertibleBondBasicPO latestByBondCode(String bondCode) {
            return this.latest;
        }

        @Override
        public void saveBasic(ConvertibleBondBasicPO basic) {
            this.saved = basic;
            this.latest = basic;
        }
    }

    private static class FakeShareManage extends ConvertibleBondShareManage {
        private ConvertibleBondSharePO latest;
        private List<ConvertibleBondSharePO> saved = List.of();

        @Override
        public ConvertibleBondSharePO latestByBondCode(String bondCode) {
            return this.latest;
        }

        @Override
        public void saveShares(List<ConvertibleBondSharePO> shares) {
            this.saved = shares;
            this.latest = shares.isEmpty() ? null : shares.get(0);
        }
    }

    private static class FakeValuationManage extends ConvertibleBondDailyValuationManage {
        private List<ConvertibleBondDailyValuationPO> valuations = List.of();

        @Override
        public List<ConvertibleBondDailyValuationPO> listByBondCode(String bondCode, Integer limit) {
            return this.valuations;
        }
    }

    private static class FakeAssetDataEnsureService implements AssetDataEnsureService {
        private int bondEnsureCalls;
        private Runnable bondEnsureAction = () -> {
        };

        @Override
        public AssetDataEnsureResult ensureStockData(StockConfigPO stock) {
            return new AssetDataEnsureResult(false, List.of());
        }

        @Override
        public AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond) {
            this.bondEnsureCalls++;
            this.bondEnsureAction.run();
            return new AssetDataEnsureResult(true, List.of());
        }

        @Override
        public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
            return false;
        }
    }

    private static BondConfigPO bond() {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode("113001");
        bond.setBondName("测试转债");
        return bond;
    }

    private static ConvertibleBondBasicPO basic(LocalDateTime syncedAt) {
        ConvertibleBondBasicPO basic = new ConvertibleBondBasicPO();
        basic.setBondCode("113001");
        basic.setSyncedAt(syncedAt);
        return basic;
    }

    private static ConvertibleBondSharePO share(LocalDateTime syncedAt) {
        ConvertibleBondSharePO share = new ConvertibleBondSharePO();
        share.setBondCode("113001");
        share.setSyncedAt(syncedAt);
        return share;
    }

    private static ConvertibleBondDailyValuationPO valuation(
            LocalDate tradeDate,
            LocalDateTime syncedAt,
            String closePrice,
            String conversionValue,
            String premiumRate) {
        ConvertibleBondDailyValuationPO valuation = new ConvertibleBondDailyValuationPO();
        valuation.setBondCode("113001");
        valuation.setTradeDate(tradeDate);
        valuation.setSyncedAt(syncedAt);
        valuation.setClosePrice(new BigDecimal(closePrice));
        valuation.setConversionValue(new BigDecimal(conversionValue));
        valuation.setPremiumRate(new BigDecimal(premiumRate));
        valuation.setPureBondValue(new BigDecimal("96.50"));
        valuation.setPureBondPremiumRate(new BigDecimal("22.00"));
        valuation.setYtm(new BigDecimal("1.82"));
        valuation.setSource("tushare");
        return valuation;
    }

    private static BondQuoteSnapshotPO quote(LocalDateTime syncedAt) {
        BondQuoteSnapshotPO quote = new BondQuoteSnapshotPO();
        quote.setBondCode("113001");
        quote.setSyncedAt(syncedAt);
        quote.setLatestPrice(new BigDecimal("120.00"));
        quote.setConversionValue(new BigDecimal("94.00"));
        quote.setConversionPremiumRate(new BigDecimal("27.66"));
        quote.setVolume(1200L);
        quote.setTurnoverAmount(new BigDecimal("8800000"));
        return quote;
    }
}
