package com.scrapider.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.scrapider.finance.service.AssetDataEnsureResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AssetDataEnsureServiceImplTest {

    @Test
    void ensureConvertibleBondDailyValuationsReturnsFalseWhenProviderReturnsNoValuation() {
        FakeDailyValuationManage valuationManage = new FakeDailyValuationManage();
        FakeConvertibleBondDataProvider provider = new FakeConvertibleBondDataProvider();
        AssetDataEnsureServiceImpl service = this.service(valuationManage, provider);

        boolean ensured = service.ensureConvertibleBondDailyValuations(bond());

        assertThat(ensured).isFalse();
        assertThat(provider.dailyValuationCallCount.get()).isEqualTo(1);
        assertThat(valuationManage.latest).isNull();
    }

    @Test
    void ensureConvertibleBondDailyValuationsReturnsTrueOnlyAfterValuationIsPersisted() {
        FakeDailyValuationManage valuationManage = new FakeDailyValuationManage();
        FakeConvertibleBondDataProvider provider = new FakeConvertibleBondDataProvider();
        provider.dailyValuations = List.of(dailyValuation());
        AssetDataEnsureServiceImpl service = this.service(valuationManage, provider);

        boolean ensured = service.ensureConvertibleBondDailyValuations(bond());

        assertThat(ensured).isTrue();
        assertThat(provider.dailyValuationCallCount.get()).isEqualTo(1);
        assertThat(valuationManage.latest).isSameAs(provider.dailyValuations.get(0));
    }

    @Test
    void concurrentDailyValuationEnsuresShareTheSameBondRefresh() throws Exception {
        FakeDailyValuationManage valuationManage = new FakeDailyValuationManage();
        FakeConvertibleBondDataProvider provider = new FakeConvertibleBondDataProvider();
        provider.dailyValuations = List.of(dailyValuation());
        provider.dailyCallStarted = new CountDownLatch(1);
        provider.secondDailyCallStarted = new CountDownLatch(1);
        provider.allowDailyCallReturn = new CountDownLatch(1);
        AssetDataEnsureServiceImpl service = this.service(valuationManage, provider);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondEnsureStarted = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> service.ensureConvertibleBondDailyValuations(bond()));
            assertThat(provider.dailyCallStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> {
                secondEnsureStarted.countDown();
                return service.ensureConvertibleBondDailyValuations(bond());
            });
            assertThat(secondEnsureStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(provider.secondDailyCallStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            provider.allowDailyCallReturn.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(provider.dailyValuationCallCount.get()).isEqualTo(1);
        } finally {
            provider.allowDailyCallReturn.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void ensureStockDataBackfillsIndustryFromValuationWhenProviderHasNoIndustry() {
        FakeStockIndustryInfoManage industryManage = new FakeStockIndustryInfoManage();
        FakeStockValuationHistoryManage valuationManage = new FakeStockValuationHistoryManage();
        FakeStockFundamentalProvider provider = new FakeStockFundamentalProvider();
        provider.valuations = List.of(valuationWithBoard());
        AssetDataEnsureServiceImpl service = new AssetDataEnsureServiceImpl(
                provider,
                industryManage,
                valuationManage,
                new FakeStockFinancialIndicatorManage(),
                new FakeStockDividendHistoryManage(),
                new FakeBasicManage(),
                new FakeShareManage(),
                new FakeDailyValuationManage(),
                provider(new FakeConvertibleBondDataProvider()),
                250);

        AssetDataEnsureResult result = service.ensureStockData(stock());

        assertThat(result.refreshAttempted()).isTrue();
        assertThat(result.unavailableSections()).doesNotContain("industry");
        assertThat(industryManage.saved).isNotNull();
        assertThat(industryManage.saved.getIndustryName()).isEqualTo("银行");
    }

    private AssetDataEnsureServiceImpl service(
            FakeDailyValuationManage valuationManage,
            FakeConvertibleBondDataProvider provider) {
        return new AssetDataEnsureServiceImpl(
                new FakeStockFundamentalProvider(),
                new FakeStockIndustryInfoManage(),
                new FakeStockValuationHistoryManage(),
                new FakeStockFinancialIndicatorManage(),
                new FakeStockDividendHistoryManage(),
                new FakeBasicManage(),
                new FakeShareManage(),
                valuationManage,
                provider(provider),
                250);
    }

    private static class FakeStockFundamentalProvider implements StockFundamentalProvider {
        private List<StockValuationHistoryPO> valuations = List.of();

        @Override
        public StockIndustryInfoPO getIndustryInfo(StockConfigPO stockConfig) {
            return null;
        }

        @Override
        public List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit) {
            return this.valuations;
        }

        @Override
        public List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit) {
            return List.of();
        }

        @Override
        public List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit) {
            return List.of();
        }
    }

    private static class FakeConvertibleBondDataProvider implements ConvertibleBondDataProvider {
        private List<ConvertibleBondDailyValuationPO> dailyValuations = List.of();
        private final AtomicInteger dailyValuationCallCount = new AtomicInteger();
        private CountDownLatch dailyCallStarted;
        private CountDownLatch secondDailyCallStarted;
        private CountDownLatch allowDailyCallReturn;

        @Override
        public ConvertibleBondBasicPO getBasic(BondConfigPO bond) {
            return null;
        }

        @Override
        public List<ConvertibleBondDailyValuationPO> getDailyValuations(BondConfigPO bond, Integer limit) {
            int callCount = this.dailyValuationCallCount.incrementAndGet();
            if (callCount > 1 && this.secondDailyCallStarted != null) {
                this.secondDailyCallStarted.countDown();
            }
            if (this.dailyCallStarted != null) {
                this.dailyCallStarted.countDown();
                await(this.allowDailyCallReturn);
            }
            return this.dailyValuations;
        }

        @Override
        public List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit) {
            return List.of();
        }
    }

    private static class FakeStockIndustryInfoManage extends StockIndustryInfoManage {
        private StockIndustryInfoPO existing;
        private StockIndustryInfoPO saved;

        @Override
        public StockIndustryInfoPO getBySecid(String secid) {
            return this.existing;
        }

        @Override
        public void saveIndustryInfo(StockIndustryInfoPO industryInfo) {
            this.saved = industryInfo;
            this.existing = industryInfo;
        }
    }

    private static class FakeStockValuationHistoryManage extends StockValuationHistoryManage {
        private StockValuationHistoryPO latest;

        @Override
        public StockValuationHistoryPO latestByStockCode(String stockCode) {
            return this.latest;
        }

        @Override
        public void saveValuationHistory(List<StockValuationHistoryPO> valuations) {
            this.latest = valuations.get(0);
        }
    }

    private static class FakeStockFinancialIndicatorManage extends StockFinancialIndicatorManage {
        @Override
        public StockFinancialIndicatorPO latestByStockCode(String stockCode) {
            return null;
        }
    }

    private static class FakeStockDividendHistoryManage extends StockDividendHistoryManage {
        @Override
        public StockDividendHistoryPO latestByStockCode(String stockCode) {
            return null;
        }
    }

    private static class FakeBasicManage extends ConvertibleBondBasicManage {
        @Override
        public ConvertibleBondBasicPO latestByBondCode(String bondCode) {
            return null;
        }
    }

    private static class FakeShareManage extends ConvertibleBondShareManage {
        @Override
        public ConvertibleBondSharePO latestByBondCode(String bondCode) {
            return null;
        }
    }

    private static class FakeDailyValuationManage extends ConvertibleBondDailyValuationManage {
        private ConvertibleBondDailyValuationPO latest;

        @Override
        public ConvertibleBondDailyValuationPO latestByBondCode(String bondCode) {
            return this.latest;
        }

        @Override
        public void saveValuations(List<ConvertibleBondDailyValuationPO> valuations) {
            if (!valuations.isEmpty()) {
                this.latest = valuations.get(0);
            }
        }
    }

    private static ObjectProvider<ConvertibleBondDataProvider> provider(ConvertibleBondDataProvider provider) {
        return new ObjectProvider<>() {
            @Override
            public ConvertibleBondDataProvider getObject(Object... args) {
                return provider;
            }

            @Override
            public ConvertibleBondDataProvider getIfAvailable() {
                return provider;
            }

            @Override
            public ConvertibleBondDataProvider getIfUnique() {
                return provider;
            }

            @Override
            public ConvertibleBondDataProvider getObject() {
                return provider;
            }
        };
    }

    private static BondConfigPO bond() {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode("113001");
        return bond;
    }

    private static StockConfigPO stock() {
        StockConfigPO stock = new StockConfigPO();
        stock.setStockCode("600000");
        stock.setStockName("浦发银行");
        stock.setSecid("1.600000");
        stock.setMarketCode("1");
        stock.setExchangeCode("SH");
        return stock;
    }

    private static ConvertibleBondDailyValuationPO dailyValuation() {
        ConvertibleBondDailyValuationPO valuation = new ConvertibleBondDailyValuationPO();
        valuation.setBondCode("113001");
        valuation.setTradeDate(LocalDate.now());
        valuation.setSyncedAt(LocalDateTime.now());
        return valuation;
    }

    private static StockValuationHistoryPO valuationWithBoard() {
        StockValuationHistoryPO valuation = new StockValuationHistoryPO();
        valuation.setStockCode("600000");
        valuation.setSecid("1.600000");
        valuation.setTradeDate(LocalDate.now());
        valuation.setSyncedAt(LocalDateTime.now());
        valuation.setBoardName("银行");
        return valuation;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (latch != null && !latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发测试放行超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
