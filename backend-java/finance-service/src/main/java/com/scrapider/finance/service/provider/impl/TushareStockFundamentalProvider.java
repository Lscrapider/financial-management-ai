package com.scrapider.finance.service.provider.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scrapider.finance.service.provider.StockFundamentalProvider;
import com.scrapider.finance.api.TushareApi;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.stock-fundamental", havingValue = "tushare")
public class TushareStockFundamentalProvider implements StockFundamentalProvider {

    private static final String STOCK_BASIC_FIELDS = "ts_code,symbol,name,area,industry,market,exchange,list_status";
    private static final String DAILY_BASIC_FIELDS =
            "ts_code,trade_date,close,total_mv,circ_mv,total_share,float_share,pe_ttm,pb,ps_ttm";
    private static final String FIN_INDICATOR_FIELDS =
            "ts_code,ann_date,end_date,eps,bps,revenue,profit_to_gr,q_profit_yoy,roe,debt_to_assets";
    private static final String DIVIDEND_FIELDS =
            "ts_code,end_date,ann_date,record_date,ex_date,cash_div,cash_div_tax,div_proc";
    private static final DateTimeFormatter TUSHARE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final TushareApi tushareApi;

    public TushareStockFundamentalProvider(TushareApi tushareApi) {
        this.tushareApi = tushareApi;
    }

    @Override
    public StockIndustryInfoPO getIndustryInfo(StockConfigPO stockConfig) {
        ArrayNode rows = this.tushareApi.queryRows(
                "stock_basic",
                Map.of("ts_code", this.toTsCode(stockConfig)),
                STOCK_BASIC_FIELDS);
        if (rows.isEmpty()) {
            return null;
        }
        return StockIndustryInfoPO.fromTushareStockBasicRow(stockConfig, rows.get(0));
    }

    @Override
    public List<StockValuationHistoryPO> getValuationHistory(StockConfigPO stockConfig, int limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "daily_basic",
                Map.of(
                        "ts_code", this.toTsCode(stockConfig),
                        "start_date", this.startDate(limit * 2L),
                        "end_date", this.endDate(),
                        "limit", limit),
                DAILY_BASIC_FIELDS);
        return StockValuationHistoryPO.fromTushareRows(stockConfig, rows);
    }

    @Override
    public List<StockFinancialIndicatorPO> getFinancialIndicators(StockConfigPO stockConfig, int limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "fina_indicator",
                Map.of(
                        "ts_code", this.toTsCode(stockConfig),
                        "start_date", this.startDate(3650),
                        "end_date", this.endDate(),
                        "limit", limit),
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

    private String startDate(long days) {
        return LocalDate.now().minusDays(Math.max(days, 1L)).format(TUSHARE_DATE_FORMATTER);
    }

    private String endDate() {
        return LocalDate.now().format(TUSHARE_DATE_FORMATTER);
    }
}
