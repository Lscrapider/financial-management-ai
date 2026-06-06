package com.scrapider.finance.ai.service.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scrapider.finance.ai.service.StockFundamentalProvider;
import com.scrapider.finance.ai.api.TushareApi;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.stock-fundamental", havingValue = "tushare")
public class TushareStockFundamentalProvider implements StockFundamentalProvider {

    private static final String DAILY_BASIC_FIELDS =
            "ts_code,trade_date,close,total_mv,circ_mv,total_share,float_share,pe_ttm,pb,ps_ttm";
    private static final String FIN_INDICATOR_FIELDS =
            "ts_code,ann_date,end_date,eps,bps,revenue,profit_to_gr,q_profit_yoy,roe,debt_to_assets";
    private static final String DIVIDEND_FIELDS =
            "ts_code,end_date,ann_date,record_date,ex_date,cash_div,cash_div_tax,div_proc";

    private final TushareApi tushareApi;

    public TushareStockFundamentalProvider(TushareApi tushareApi) {
        this.tushareApi = tushareApi;
    }

    @Override
    public List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "daily_basic",
                Map.of("ts_code", this.toTsCode(stockConfig), "limit", limit),
                DAILY_BASIC_FIELDS);
        return StockValuationHistoryPO.fromTushareRows(stockConfig, rows);
    }

    @Override
    public List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "fina_indicator",
                Map.of("ts_code", this.toTsCode(stockConfig), "limit", limit),
                FIN_INDICATOR_FIELDS);
        return StockFinancialIndicatorPO.fromTushareRows(stockConfig, rows);
    }

    @Override
    public List<StockDividendHistoryPO> getDividendHistory(StockConfigPO stockConfig, int limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "dividend",
                Map.of("ts_code", this.toTsCode(stockConfig), "limit", limit),
                DIVIDEND_FIELDS);
        return StockDividendHistoryPO.fromTushareRows(stockConfig, rows);
    }

    private String toTsCode(StockConfigPO stockConfig) {
        return "%s.%s".formatted(stockConfig.getStockCode(), stockConfig.getExchangeCode());
    }
}
