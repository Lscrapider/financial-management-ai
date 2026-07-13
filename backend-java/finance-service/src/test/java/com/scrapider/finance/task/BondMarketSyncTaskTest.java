package com.scrapider.finance.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BondMarketSyncTaskTest {

    @Test
    void syncConvertibleDailyDataForBondReturnsSharedEnsureResult() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        FakeAssetDataEnsureService assetDataEnsureService = new FakeAssetDataEnsureService();
        assetDataEnsureService.dailyValuationsEnsured = false;
        BondConfigPO bond = bond();
        bondConfigManage.bond = bond;
        BondMarketSyncTask task = new BondMarketSyncTask(
                null,
                bondConfigManage,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                assetDataEnsureService);
        ReflectionTestUtils.setField(task, "convertibleDataEnabled", true);

        boolean synced = task.syncConvertibleDailyDataForBond(bond.getBondCode());

        assertThat(synced).isFalse();
        assertThat(assetDataEnsureService.dailyValuationCalls).isEqualTo(1);
        assertThat(assetDataEnsureService.lastBond).isSameAs(bond);
    }

    private static class FakeBondConfigManage extends BondConfigManage {
        private BondConfigPO bond;

        @Override
        public BondConfigPO getEnabledByBondCode(String bondCode) {
            return this.bond;
        }
    }

    private static class FakeAssetDataEnsureService implements AssetDataEnsureService {
        private boolean dailyValuationsEnsured;
        private int dailyValuationCalls;
        private BondConfigPO lastBond;

        @Override
        public AssetDataEnsureResult ensureStockData(StockConfigPO stock) {
            return new AssetDataEnsureResult(false, java.util.List.of());
        }

        @Override
        public AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond) {
            return new AssetDataEnsureResult(false, java.util.List.of());
        }

        @Override
        public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
            this.dailyValuationCalls++;
            this.lastBond = bond;
            return this.dailyValuationsEnsured;
        }
    }

    private static BondConfigPO bond() {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode("113001");
        return bond;
    }
}
