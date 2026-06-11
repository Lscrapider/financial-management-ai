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
import java.time.LocalDateTime;
import java.util.ArrayList;
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
                new MarketTradingCalendarService(""));
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
    }

    @Test
    void startupCatchUpSkipsWhenTodayHasSynced() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        FakeDailyValuationManage dailyValuationManage = new FakeDailyValuationManage();
        FakeProvider provider = new FakeProvider();
        ConvertibleBondDataSyncTask task = new ConvertibleBondDataSyncTask(
                bondConfigManage,
                dailyValuationManage,
                provider(provider),
                new MarketTradingCalendarService(""));
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "catchUpEnabled", true);
        ReflectionTestUtils.setField(task, "runAfter", "19:00");
        ReflectionTestUtils.setField(task, "timezone", "Asia/Shanghai");
        dailyValuationManage.hasSyncedSince = true;

        task.catchUpAfterStartup(LocalDateTime.of(2026, 6, 8, 19, 30));

        assertThat(provider.dailyCallCount).isZero();
        assertThat(dailyValuationManage.savedBatchCount).isZero();
    }

    private static class FakeBondConfigManage extends BondConfigManage {
        private List<BondConfigPO> bonds = List.of();

        @Override
        public List<BondConfigPO> listEnabledBonds() {
            return this.bonds;
        }
    }

    private static class FakeDailyValuationManage extends ConvertibleBondDailyValuationManage {
        private boolean hasSyncedSince;
        private int savedBatchCount;

        @Override
        public boolean hasSyncedSince(LocalDateTime startAt) {
            return this.hasSyncedSince;
        }

        @Override
        public void saveValuations(List<ConvertibleBondDailyValuationPO> valuations) {
            this.savedBatchCount++;
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
