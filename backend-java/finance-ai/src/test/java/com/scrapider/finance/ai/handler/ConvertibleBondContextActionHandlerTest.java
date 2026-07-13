package com.scrapider.finance.ai.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.ConvertibleBondBasicManage;
import com.scrapider.finance.manage.ConvertibleBondDailyValuationManage;
import com.scrapider.finance.manage.ConvertibleBondShareManage;
import com.scrapider.finance.service.AssetDataEnsurePolicy;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConvertibleBondContextActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refreshesMissingDataAndReturnsReloadedConvertibleBondContext() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        bondConfigManage.bond = bond();
        FakeBasicManage basicManage = new FakeBasicManage();
        FakeDailyValuationManage valuationManage = new FakeDailyValuationManage();
        FakeShareManage shareManage = new FakeShareManage();
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ensureService.bondEnsureAction = () -> {
            basicManage.basic = basic();
            valuationManage.valuations = List.of(valuation());
            shareManage.shares = List.of(share());
        };
        ConvertibleBondContextActionHandler handler = new ConvertibleBondContextActionHandler(
                bondConfigManage,
                basicManage,
                valuationManage,
                shareManage,
                ensureService);

        AgentDataGatewayResponseVO response = handler.handle(null, this.param());

        assertThat(ensureService.bondEnsureCalls).isEqualTo(1);
        Map<String, Object> refresh = refreshMetadata(response);
        assertThat(refresh).containsEntry("attempted", true).containsEntry("status", "completed");
        Map<String, Object> row = response.data().get(0);
        assertThat(castMap(row.get("basic"))).isNotEmpty();
        assertThat((List<?>) row.get("valuationHistory")).hasSize(1);
        assertThat((List<?>) row.get("shareChanges")).hasSize(1);
    }

    @Test
    void refreshesExpiredBasicData() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        bondConfigManage.bond = bond();
        FakeBasicManage basicManage = new FakeBasicManage();
        basicManage.basic = basic();
        basicManage.basic.setSyncedAt(LocalDateTime.now()
                .minusDays(AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)
                .minusSeconds(1));
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ConvertibleBondContextActionHandler handler = new ConvertibleBondContextActionHandler(
                bondConfigManage,
                basicManage,
                new FakeDailyValuationManage(),
                new FakeShareManage(),
                ensureService);

        handler.handle(null, this.param(List.of("basic")));

        assertThat(ensureService.bondEnsureCalls).isEqualTo(1);
    }

    @Test
    void refreshesExpiredShareChanges() {
        FakeBondConfigManage bondConfigManage = new FakeBondConfigManage();
        bondConfigManage.bond = bond();
        FakeShareManage shareManage = new FakeShareManage();
        ConvertibleBondSharePO expiredShare = share();
        expiredShare.setSyncedAt(LocalDateTime.now()
                .minusDays(AssetDataEnsurePolicy.CONVERTIBLE_BOND_FRESH_DAYS)
                .minusSeconds(1));
        shareManage.shares = List.of(expiredShare);
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ConvertibleBondContextActionHandler handler = new ConvertibleBondContextActionHandler(
                bondConfigManage,
                new FakeBasicManage(),
                new FakeDailyValuationManage(),
                shareManage,
                ensureService);

        handler.handle(null, this.param(List.of("shareChanges")));

        assertThat(ensureService.bondEnsureCalls).isEqualTo(1);
    }

    private AgentDataQueryParam param() {
        return this.param(List.of("basic", "valuationHistory", "shareChanges"));
    }

    private AgentDataQueryParam param(List<String> sections) {
        return new AgentDataQueryParam(
                ConvertibleBondContextActionHandler.ACTION,
                this.objectMapper.valueToTree(Map.of(
                        "targetCode", "113001",
                        "sections", sections)),
                null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> refreshMetadata(AgentDataGatewayResponseVO response) {
        return (Map<String, Object>) response.metadata().get("dataRefresh");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static class FakeBondConfigManage extends BondConfigManage {
        private BondConfigPO bond;

        @Override
        public BondConfigPO getEnabledByBondCode(String bondCode) {
            return this.bond;
        }
    }

    private static class FakeBasicManage extends ConvertibleBondBasicManage {
        private ConvertibleBondBasicPO basic;

        @Override
        public ConvertibleBondBasicPO latestByBondCode(String bondCode) {
            return this.basic;
        }
    }

    private static class FakeDailyValuationManage extends ConvertibleBondDailyValuationManage {
        private List<ConvertibleBondDailyValuationPO> valuations = List.of();

        @Override
        public List<ConvertibleBondDailyValuationPO> listByBondCode(String bondCode, Integer limit) {
            return this.valuations;
        }
    }

    private static class FakeShareManage extends ConvertibleBondShareManage {
        private List<ConvertibleBondSharePO> shares = List.of();

        @Override
        public List<ConvertibleBondSharePO> listByBondCode(String bondCode, Integer limit) {
            return this.shares;
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
        bond.setEnabled(true);
        return bond;
    }

    private static ConvertibleBondBasicPO basic() {
        ConvertibleBondBasicPO basic = new ConvertibleBondBasicPO();
        basic.setBondCode("113001");
        basic.setUnderlyingStockCode("600000");
        basic.setUnderlyingStockName("浦发银行");
        return basic;
    }

    private static ConvertibleBondDailyValuationPO valuation() {
        ConvertibleBondDailyValuationPO valuation = new ConvertibleBondDailyValuationPO();
        valuation.setBondCode("113001");
        return valuation;
    }

    private static ConvertibleBondSharePO share() {
        ConvertibleBondSharePO share = new ConvertibleBondSharePO();
        share.setBondCode("113001");
        return share;
    }
}
