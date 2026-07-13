package com.scrapider.finance.ai.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockDividendHistoryManage;
import com.scrapider.finance.manage.StockFinancialIndicatorManage;
import com.scrapider.finance.manage.StockValuationHistoryManage;
import com.scrapider.finance.service.AssetDataEnsureResult;
import com.scrapider.finance.service.AssetDataEnsureService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockFundamentalContextActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refreshesMissingDataAndReturnsReloadedStockContext() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        stockConfigManage.stock = stock();
        FakeStockValuationHistoryManage valuationManage = new FakeStockValuationHistoryManage();
        FakeStockFinancialIndicatorManage financialManage = new FakeStockFinancialIndicatorManage();
        FakeStockDividendHistoryManage dividendManage = new FakeStockDividendHistoryManage();
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ensureService.stockEnsureAction = () -> {
            valuationManage.valuations = List.of(valuation());
            financialManage.indicators = List.of(financial());
            dividendManage.dividends = List.of(dividend());
        };
        StockFundamentalContextActionHandler handler = new StockFundamentalContextActionHandler(
                stockConfigManage,
                null,
                valuationManage,
                financialManage,
                dividendManage,
                ensureService);

        AgentDataGatewayResponseVO response = handler.handle(null, this.param());

        assertThat(ensureService.stockEnsureCalls).isEqualTo(1);
        Map<String, Object> refresh = refreshMetadata(response);
        assertThat(refresh).containsEntry("attempted", true).containsEntry("status", "completed");
        Map<String, Object> row = response.data().get(0);
        assertThat((List<?>) row.get("financialIndicators")).hasSize(1);
        Map<String, Object> completeness = castMap(row.get("dataCompleteness"));
        assertThat(completeness).containsEntry("valuationHistoryCount", 1);
        assertThat(completeness).containsEntry("financialIndicatorCount", 1);
    }

    @Test
    void keepsOriginalResponseWhenStockRefreshFails() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        stockConfigManage.stock = stock();
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ensureService.stockFailure = new IllegalStateException("刷新失败");
        StockFundamentalContextActionHandler handler = new StockFundamentalContextActionHandler(
                stockConfigManage,
                null,
                new FakeStockValuationHistoryManage(),
                new FakeStockFinancialIndicatorManage(),
                new FakeStockDividendHistoryManage(),
                ensureService);

        AgentDataGatewayResponseVO response = handler.handle(null, this.param());

        assertThat(response.success()).isTrue();
        Map<String, Object> refresh = refreshMetadata(response);
        assertThat(refresh).containsEntry("attempted", true).containsEntry("status", "refresh_failed");
        assertThat(refresh).containsEntry("failureReason", "refresh_failed");
        Map<String, Object> completeness = castMap(response.data().get(0).get("dataCompleteness"));
        assertThat(completeness).containsEntry("valuationHistoryCount", 0);
        assertThat(completeness).containsEntry("financialIndicatorCount", 0);
    }

    @Test
    void refreshesExpiredValuationData() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        stockConfigManage.stock = stock();
        FakeStockValuationHistoryManage valuationManage = new FakeStockValuationHistoryManage();
        FakeStockFinancialIndicatorManage financialManage = new FakeStockFinancialIndicatorManage();
        valuationManage.valuations = List.of(valuation(LocalDateTime.now().minusDays(1)));
        financialManage.indicators = List.of(financial(LocalDateTime.now()));
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ensureService.stockEnsureAction = () -> valuationManage.valuations = List.of(valuation(LocalDateTime.now()));
        StockFundamentalContextActionHandler handler = new StockFundamentalContextActionHandler(
                stockConfigManage,
                null,
                valuationManage,
                financialManage,
                new FakeStockDividendHistoryManage(),
                ensureService);

        AgentDataGatewayResponseVO response = handler.handle(null, this.param());

        assertThat(ensureService.stockEnsureCalls).isEqualTo(1);
        assertThat(refreshMetadata(response)).containsEntry("status", "completed");
    }

    @Test
    void refreshesWhenValuationIsFreshButDividendDataIsMissing() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        stockConfigManage.stock = stock();
        FakeStockValuationHistoryManage valuationManage = new FakeStockValuationHistoryManage();
        valuationManage.valuations = List.of(valuation(LocalDateTime.now()));
        FakeStockDividendHistoryManage dividendManage = new FakeStockDividendHistoryManage();
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        ensureService.stockEnsureAction = () -> dividendManage.dividends = List.of(dividend(LocalDateTime.now()));
        StockFundamentalContextActionHandler handler = new StockFundamentalContextActionHandler(
                stockConfigManage,
                null,
                valuationManage,
                new FakeStockFinancialIndicatorManage(),
                dividendManage,
                ensureService);

        AgentDataGatewayResponseVO response = handler.handle(null, this.param(List.of("valuation")));

        assertThat(ensureService.stockEnsureCalls).isEqualTo(1);
        Map<String, Object> completeness = castMap(response.data().get(0).get("dataCompleteness"));
        assertThat(completeness).containsEntry("dividendHistoryCount", 1);
    }

    @Test
    void refreshesExpiredDividendData() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        stockConfigManage.stock = stock();
        FakeStockValuationHistoryManage valuationManage = new FakeStockValuationHistoryManage();
        valuationManage.valuations = List.of(valuation(LocalDateTime.now()));
        FakeStockDividendHistoryManage dividendManage = new FakeStockDividendHistoryManage();
        dividendManage.dividends = List.of(dividend(LocalDateTime.now().minusDays(31)));
        FakeAssetDataEnsureService ensureService = new FakeAssetDataEnsureService();
        StockFundamentalContextActionHandler handler = new StockFundamentalContextActionHandler(
                stockConfigManage,
                null,
                valuationManage,
                new FakeStockFinancialIndicatorManage(),
                dividendManage,
                ensureService);

        handler.handle(null, this.param(List.of("valuation")));

        assertThat(ensureService.stockEnsureCalls).isEqualTo(1);
    }

    private AgentDataQueryParam param() {
        return this.param(List.of("valuation", "financialIndicators"));
    }

    private AgentDataQueryParam param(List<String> sections) {
        return new AgentDataQueryParam(
                StockFundamentalContextActionHandler.ACTION,
                this.objectMapper.valueToTree(Map.of(
                        "targetCode", "600000",
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

    private static class FakeStockConfigManage extends StockConfigManage {
        private StockConfigPO stock;

        @Override
        public StockConfigPO getByStockCode(String stockCode) {
            return this.stock;
        }

        @Override
        public StockConfigPO getEnabledByStockCode(String stockCode) {
            return this.stock;
        }
    }

    private static class FakeStockValuationHistoryManage extends StockValuationHistoryManage {
        private List<StockValuationHistoryPO> valuations = List.of();

        @Override
        public List<StockValuationHistoryPO> listByStockCode(String stockCode, Integer limit) {
            return this.valuations;
        }
    }

    private static class FakeStockFinancialIndicatorManage extends StockFinancialIndicatorManage {
        private List<StockFinancialIndicatorPO> indicators = List.of();

        @Override
        public List<StockFinancialIndicatorPO> listByStockCode(String stockCode, Integer limit) {
            return this.indicators;
        }
    }

    private static class FakeStockDividendHistoryManage extends StockDividendHistoryManage {
        private List<StockDividendHistoryPO> dividends = List.of();

        @Override
        public List<StockDividendHistoryPO> listByStockCode(String stockCode, Integer limit) {
            return this.dividends;
        }
    }

    private static class FakeAssetDataEnsureService implements AssetDataEnsureService {
        private int stockEnsureCalls;
        private Runnable stockEnsureAction = () -> {
        };
        private RuntimeException stockFailure;

        @Override
        public AssetDataEnsureResult ensureStockData(StockConfigPO stock) {
            this.stockEnsureCalls++;
            if (this.stockFailure != null) {
                throw this.stockFailure;
            }
            this.stockEnsureAction.run();
            return new AssetDataEnsureResult(true, List.of());
        }

        @Override
        public AssetDataEnsureResult ensureConvertibleBondData(BondConfigPO bond) {
            return new AssetDataEnsureResult(false, List.of());
        }

        @Override
        public boolean ensureConvertibleBondDailyValuations(BondConfigPO bond) {
            return false;
        }
    }

    private static StockConfigPO stock() {
        StockConfigPO stock = new StockConfigPO();
        stock.setStockCode("600000");
        stock.setStockName("浦发银行");
        stock.setSecid("1.600000");
        stock.setEnabled(true);
        return stock;
    }

    private static StockValuationHistoryPO valuation() {
        return valuation(null);
    }

    private static StockValuationHistoryPO valuation(LocalDateTime syncedAt) {
        StockValuationHistoryPO valuation = new StockValuationHistoryPO();
        valuation.setStockCode("600000");
        valuation.setSyncedAt(syncedAt);
        return valuation;
    }

    private static StockFinancialIndicatorPO financial() {
        return financial(null);
    }

    private static StockFinancialIndicatorPO financial(LocalDateTime syncedAt) {
        StockFinancialIndicatorPO financial = new StockFinancialIndicatorPO();
        financial.setStockCode("600000");
        financial.setSyncedAt(syncedAt);
        return financial;
    }

    private static StockDividendHistoryPO dividend() {
        return dividend(null);
    }

    private static StockDividendHistoryPO dividend(LocalDateTime syncedAt) {
        StockDividendHistoryPO dividend = new StockDividendHistoryPO();
        dividend.setStockCode("600000");
        dividend.setSyncedAt(syncedAt);
        return dividend;
    }
}
