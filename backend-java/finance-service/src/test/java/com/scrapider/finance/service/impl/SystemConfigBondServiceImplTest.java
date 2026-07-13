package com.scrapider.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.scrapider.finance.domain.param.BondConfigAddParam;
import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.vo.BondConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.provider.ConvertibleBondDataProvider;
import com.scrapider.finance.service.AssetDataInitializationService;
import com.scrapider.finance.service.SystemConfigStockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SystemConfigBondServiceImplTest {

    @Test
    void addBondSchedulesBackgroundInitializationInsteadOfSynchronizingSlowData() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        FakeConvertibleBondBasicManage basicManage = new FakeConvertibleBondBasicManage();
        FakeAssetDataInitializationService initializationService = new FakeAssetDataInitializationService();
        SystemConfigBondServiceImpl service = new SystemConfigBondServiceImpl(
                provider(new FakeConvertibleBondDataProvider()),
                bondConfigManage,
                basicManage,
                new FakeSystemConfigStockService(),
                initializationService);

        BondConfigAddResultVO result = service.addBond(param());

        assertThat(result.getBasicSynced()).isTrue();
        assertThat(result.getUnderlyingStockSynced()).isTrue();
        assertThat(result.getMarketDataSynced()).isFalse();
        assertThat(result.getDailyValuationSynced()).isFalse();
        assertThat(result.getShareSynced()).isFalse();
        assertThat(result.getInitializationScheduled()).isTrue();
        assertThat(bondConfigManage.saved).isNotNull();
        assertThat(basicManage.saved).isNotNull();
        assertThat(initializationService.scheduledBond).isSameAs(bondConfigManage.saved);
    }

    private static class FakeConvertibleBondDataProvider implements ConvertibleBondDataProvider {
        @Override
        public ConvertibleBondBasicPO getBasic(BondConfigPO bond) {
            ConvertibleBondBasicPO basic = new ConvertibleBondBasicPO();
            basic.setBondCode("113001");
            basic.setBondName("测试转债");
            basic.setUnderlyingStockCode("600000");
            basic.setUnderlyingStockName("浦发银行");
            return basic;
        }

        @Override
        public List<ConvertibleBondDailyValuationPO> getDailyValuations(BondConfigPO bond, Integer limit) {
            return List.of();
        }

        @Override
        public List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit) {
            return List.of();
        }
    }

    private static class FakeBondConfigManage extends BondConfigManage {
        private BondConfigPO saved;

        @Override
        public void saveConfig(BondConfigPO bondConfig) {
            this.saved = bondConfig;
        }
    }

    private static class FakeConvertibleBondBasicManage extends ConvertibleBondBasicManage {
        private ConvertibleBondBasicPO saved;

        @Override
        public void saveBasic(ConvertibleBondBasicPO basic) {
            this.saved = basic;
        }
    }

    private static class FakeSystemConfigStockService implements SystemConfigStockService {
        @Override
        public StockConfigAddResultVO addStock(StockConfigAddParam param) {
            StockConfigAddResultVO result = new StockConfigAddResultVO();
            result.setStockCode(param.getStockCode());
            result.setStockName(param.getStockName());
            result.setQuoteSynced(true);
            return result;
        }
    }

    private static class FakeAssetDataInitializationService implements AssetDataInitializationService {
        private BondConfigPO scheduledBond;

        @Override
        public boolean scheduleStockInitialization(StockConfigPO stock) {
            return true;
        }

        @Override
        public boolean scheduleConvertibleBondInitialization(BondConfigPO bond) {
            this.scheduledBond = bond;
            return true;
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

    private static BondConfigAddParam param() {
        BondConfigAddParam param = new BondConfigAddParam();
        param.setBondCode("113001");
        param.setBondName("测试转债");
        return param;
    }
}
