package com.scrapider.finance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.scrapider.finance.api.StockMarketApi;
import com.scrapider.finance.domain.dto.StockMarketDataDTO;
import com.scrapider.finance.domain.param.StockConfigAddParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.domain.vo.StockConfigAddResultVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.AssetDataInitializationService;
import org.junit.jupiter.api.Test;

class SystemConfigStockServiceImplTest {

    @Test
    void addStockSchedulesBackgroundInitializationInsteadOfSynchronizingTrend() {
        FakeStockConfigManage stockConfigManage = new FakeStockConfigManage();
        FakeStockQuoteSnapshotManage quoteSnapshotManage = new FakeStockQuoteSnapshotManage();
        FakeAssetDataInitializationService initializationService = new FakeAssetDataInitializationService();
        SystemConfigStockServiceImpl service = new SystemConfigStockServiceImpl(
                new FakeStockMarketApi(),
                stockConfigManage,
                quoteSnapshotManage,
                initializationService);

        StockConfigAddResultVO result = service.addStock(param());

        assertThat(result.getQuoteSynced()).isTrue();
        assertThat(result.getTrendSynced()).isFalse();
        assertThat(result.getInitializationScheduled()).isTrue();
        assertThat(stockConfigManage.saved).isNotNull();
        assertThat(quoteSnapshotManage.saved).isNotNull();
        assertThat(initializationService.scheduledStock).isSameAs(stockConfigManage.saved);
    }

    @Test
    void resultFactoryKeepsTrendAndInitializationStatesExplicit() {
        StockQuoteVO quote = new StockQuoteVO();
        quote.setStockCode("600000");
        quote.setStockName("浦发银行");
        quote.setSecid("1.600000");
        quote.setMarketCode("SH_MAIN");
        quote.setExchangeCode("SH");

        StockConfigAddResultVO result = StockConfigAddResultVO.of(quote, true, false);

        assertThat(result.getTrendSynced()).isTrue();
        assertThat(result.getInitializationScheduled()).isFalse();
    }

    private static class FakeStockMarketApi extends StockMarketApi {
        private FakeStockMarketApi() {
            super(null, new ObjectMapper());
        }

        @Override
        public StockMarketDataDTO getQuote(String secid) {
            return new StockMarketDataDTO(
                    "test",
                    "",
                    JsonNodeFactory.instance.textNode("v_sh600000=\"51~浦发银行~600000~10.00\";"));
        }
    }

    private static class FakeStockConfigManage extends StockConfigManage {
        private StockConfigPO saved;

        @Override
        public void saveConfig(StockConfigPO stockConfig) {
            this.saved = stockConfig;
        }
    }

    private static class FakeStockQuoteSnapshotManage extends StockQuoteSnapshotManage {
        private StockQuoteSnapshotPO saved;

        @Override
        public void saveLatest(StockQuoteSnapshotPO snapshot) {
            this.saved = snapshot;
        }
    }

    private static class FakeAssetDataInitializationService implements AssetDataInitializationService {
        private StockConfigPO scheduledStock;

        @Override
        public boolean scheduleStockInitialization(StockConfigPO stock) {
            this.scheduledStock = stock;
            return true;
        }

        @Override
        public boolean scheduleConvertibleBondInitialization(BondConfigPO bond) {
            return true;
        }
    }

    private static StockConfigAddParam param() {
        StockConfigAddParam param = new StockConfigAddParam();
        param.setStockCode("600000");
        param.setStockName("浦发银行");
        return param;
    }
}
