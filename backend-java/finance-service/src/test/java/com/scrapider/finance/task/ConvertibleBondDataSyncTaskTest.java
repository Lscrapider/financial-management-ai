package com.scrapider.finance.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.provider.ConvertibleBondDataProvider;
import com.scrapider.finance.service.MarketTradingCalendarService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class ConvertibleBondDataSyncTaskTest {

    @Test
    void startupCatchUpSyncsDailyOnlyAfterRunTimeWhenTodayHasNotSynced() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        FakeDailyValuationManage dailyValuationManage = new FakeDailyValuationManage();
        FakeProvider provider = new FakeProvider();
        ConvertibleBondDataSyncTask task = new ConvertibleBondDataSyncTask(
                bondConfigManage,
                dailyValuationManage,
                provider(provider),
                new AlwaysTradingDayCalendarService());
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "catchUpEnabled", true);
        ReflectionTestUtils.setField(task, "runAfter", "19:00");
        ReflectionTestUtils.setField(task, "timezone", "Asia/Shanghai");
        ReflectionTestUtils.setField(task, "dailyLimit", 250);
        ReflectionTestUtils.setField(task, "requestIntervalMs", 0L);
        bondConfigManage.bonds = List.of(bond("113001"), bond("113002"));

        task.catchUpAfterStartup(LocalDateTime.of(2026, 6, 8, 19, 30));

        assertThat(provider.dailyCallCount).isEqualTo(2);
        assertThat(provider.basicCallCount).isZero();
        assertThat(provider.shareCallCount).isZero();
        assertThat(dailyValuationManage.savedBatchCount).isEqualTo(2);
        assertThat(dailyValuationManage.requestedBondCodes).containsExactly("113001", "113002");
        assertThat(dailyValuationManage.requestedTradeDate).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    void startupCatchUpSkipsWhenAllEnabledBondsHaveTodayValuations() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        FakeDailyValuationManage dailyValuationManage = new FakeDailyValuationManage();
        FakeProvider provider = new FakeProvider();
        ConvertibleBondDataSyncTask task = new ConvertibleBondDataSyncTask(
                bondConfigManage,
                dailyValuationManage,
                provider(provider),
                new AlwaysTradingDayCalendarService());
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "catchUpEnabled", true);
        ReflectionTestUtils.setField(task, "runAfter", "19:00");
        ReflectionTestUtils.setField(task, "timezone", "Asia/Shanghai");
        bondConfigManage.bonds = List.of(bond("113001"), bond("113002"));
        dailyValuationManage.hasValuationsForAllBondCodes = true;

        task.catchUpAfterStartup(LocalDateTime.of(2026, 6, 8, 19, 30));

        assertThat(provider.dailyCallCount).isZero();
        assertThat(dailyValuationManage.savedBatchCount).isZero();
        assertThat(dailyValuationManage.requestedBondCodes).containsExactly("113001", "113002");
        assertThat(dailyValuationManage.requestedTradeDate).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    private static class FakeBondConfigManage extends BondConfigManage {
        private List<BondConfigPO> bonds = List.of();

        @Override
        public List<BondConfigPO> listEnabledBonds() {
            return this.bonds;
        }
    }

    private static class FakeDailyValuationManage extends ConvertibleBondDailyValuationManage {
        private boolean hasValuationsForAllBondCodes;
        private int savedBatchCount;
        private List<String> requestedBondCodes = List.of();
        private LocalDate requestedTradeDate;

        @Override
        public boolean hasValuationsForAllBondCodes(Collection<String> bondCodes, LocalDate tradeDate) {
            this.requestedBondCodes = List.copyOf(bondCodes);
            this.requestedTradeDate = tradeDate;
            return this.hasValuationsForAllBondCodes;
        }

        @Override
        public void saveValuations(List<ConvertibleBondDailyValuationPO> valuations) {
            this.savedBatchCount++;
        }
    }

    private static class AlwaysTradingDayCalendarService extends MarketTradingCalendarService {
        private AlwaysTradingDayCalendarService() {
            super(null, null);
        }

        @Override
        public boolean isTradingDay(LocalDate date) {
            return true;
        }
    }

    private static class FakeProvider implements ConvertibleBondDataProvider {
        private int basicCallCount;
        private int dailyCallCount;
        private int shareCallCount;

        @Override
        public ConvertibleBondBasicPO getBasic(BondConfigPO bond) {
            this.basicCallCount++;
            return null;
        }

        @Override
        public List<ConvertibleBondDailyValuationPO> getDailyValuations(BondConfigPO bond, Integer limit) {
            this.dailyCallCount++;
            return List.of(new ConvertibleBondDailyValuationPO());
        }

        @Override
        public List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit) {
            this.shareCallCount++;
            return new ArrayList<>();
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

    private static BondConfigPO bond(String bondCode) {
        BondConfigPO bond = new BondConfigPO();
        bond.setBondCode(bondCode);
        bond.setBondName("测试转债");
        return bond;
    }
}
