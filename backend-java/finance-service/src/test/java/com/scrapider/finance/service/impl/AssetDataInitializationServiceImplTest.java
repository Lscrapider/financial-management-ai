package com.scrapider.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import com.scrapider.finance.task.BondMarketSyncTask;
import com.scrapider.finance.task.StockMarketSyncTask;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AssetDataInitializationServiceImplTest {

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void scheduleStockInitializationReturnsBeforeSlowTasksFinish() throws InterruptedException {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskFinish = new CountDownLatch(1);
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        AssetDataInitializationServiceImpl service = new AssetDataInitializationServiceImpl(
                new FakeStockMarketSyncTask(taskStarted, allowTaskFinish),
                new FakeBondMarketSyncTask(new CountDownLatch(0), new CountDownLatch(0)),
                ensureService);

        boolean scheduled = service.scheduleStockInitialization(stock());

        try {
            assertThat(scheduled).isTrue();
            assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(ensureService.stockEnsureCalls).isZero();
        } finally {
            allowTaskFinish.countDown();
        }
        assertThat(ensureService.stockEnsureFinished.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(ensureService.stockEnsureCalls).isEqualTo(1);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void scheduleConvertibleBondInitializationRunsMarketAndEnsureInBackground() throws InterruptedException {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskFinish = new CountDownLatch(1);
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        AssetDataInitializationServiceImpl service = new AssetDataInitializationServiceImpl(
                new FakeStockMarketSyncTask(new CountDownLatch(0), new CountDownLatch(0)),
                new FakeBondMarketSyncTask(taskStarted, allowTaskFinish),
                ensureService);

        boolean scheduled = service.scheduleConvertibleBondInitialization(bond());

        try {
            assertThat(scheduled).isTrue();
            assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(ensureService.bondEnsureCalls).isZero();
        } finally {
            allowTaskFinish.countDown();
        }
        assertThat(ensureService.bondEnsureFinished.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(ensureService.bondEnsureCalls).isEqualTo(1);
    }

    private static class FakeStockMarketSyncTask extends StockMarketSyncTask {
        private final CountDownLatch taskStarted;
        private final CountDownLatch allowTaskFinish;

        private FakeStockMarketSyncTask(CountDownLatch taskStarted, CountDownLatch allowTaskFinish) {
            super(null, null, null, null, null, null, null, null, null);
            this.taskStarted = taskStarted;
            this.allowTaskFinish = allowTaskFinish;
        }

        @Override
        public boolean syncStockTrend(String stockCode) {
            this.taskStarted.countDown();
            await(this.allowTaskFinish);
            return true;
        }
    }

    private static class FakeBondMarketSyncTask extends BondMarketSyncTask {
        private final CountDownLatch taskStarted;
        private final CountDownLatch allowTaskFinish;

        private FakeBondMarketSyncTask(CountDownLatch taskStarted, CountDownLatch allowTaskFinish) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.taskStarted = taskStarted;
            this.allowTaskFinish = allowTaskFinish;
        }

        @Override
        public boolean syncMarketDataForBond(String bondCode) {
            this.taskStarted.countDown();
            await(this.allowTaskFinish);
            return true;
        }
    }

    private static class FakeAssetDataEnsureService implements AssetDataEnsureService {
        private int stockEnsureCalls;
        private int bondEnsureCalls;
        private final CountDownLatch stockEnsureFinished = new CountDownLatch(1);
        private final CountDownLatch bondEnsureFinished = new CountDownLatch(1);

        @Override
        public AssetDataEnsureResult ensureStockData(StockConfigPO stock) {
            this.stockEnsureCalls++;
            this.stockEnsureFinished.countDown();
            return new AssetDataEnsureResult(true, List.of());
        }

        @Override
        public AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond) {
            this.bondEnsureCalls++;
            this.bondEnsureFinished.countDown();
            return new AssetDataEnsureResult(true, List.of());
        }

        @Override
        public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
            return true;
        }
    }

    private static StockConfigPO stock() {
        StockConfigPO stock = new StockConfigPO();
        stock.setStockCode("600000");
        stock.setSecid("1.600000");
        return stock;
    }

    private static BondConfigPO bond() {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode("113001");
        bond.setSecid("1.113001");
        return bond;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
