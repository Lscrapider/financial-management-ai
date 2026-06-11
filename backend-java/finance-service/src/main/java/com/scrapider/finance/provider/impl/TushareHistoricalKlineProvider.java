package com.scrapider.finance.provider.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scrapider.finance.api.TushareApi;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.provider.HistoricalKlineProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.historical-kline", havingValue = "tushare")
public class TushareHistoricalKlineProvider implements HistoricalKlineProvider {

    private static final String DAILY_FIELDS =
            "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount";
    private static final String ADJ_FACTOR_FIELDS = "ts_code,trade_date,adj_factor";
    private static final String CB_DAILY_FIELDS =
            "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount,"
                    + "bond_value,bond_over_rate,cb_value,cb_over_rate";

    private final TushareApi tushareApi;

    public TushareHistoricalKlineProvider(TushareApi tushareApi) {
        this.tushareApi = tushareApi;
    }

    @Override
    public List<StockKlinePO> getStockKlines(
            StockConfigPO stock,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                this.stockApiName(periodType),
                Map.of(
                        "ts_code", this.toTsCode(stock.getStockCode(), stock.getExchangeCode()),
                        "start_date", this.startDate(periodType, limit)),
                DAILY_FIELDS);
        Map<LocalDate, BigDecimal> adjustFactors = this.stockAdjustFactors(stock, periodType, limit);
        return StockKlinePO.fromTushareRows(stock, rows, periodType, adjustType, adjustFactors);
    }

    @Override
    public List<IndexKlinePO> getIndexKlines(IndexConfigPO index, KlinePeriodTypeEnum periodType, Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                this.indexApiName(periodType),
                Map.of(
                        "ts_code", this.toTsCode(index.getIndexCode(), index.getExchangeCode()),
                        "start_date", this.startDate(periodType, limit)),
                DAILY_FIELDS);
        return IndexKlinePO.fromTushareRows(index, rows, periodType);
    }

    @Override
    public List<BondKlinePO> getBondKlines(BondConfigPO bond, KlinePeriodTypeEnum periodType, Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "cb_daily",
                Map.of(
                        "ts_code", this.toTsCode(bond.getBondCode(), bond.getExchangeCode()),
                        "start_date", this.startDate(periodType, limit)),
                CB_DAILY_FIELDS);
        return BondKlinePO.fromTushareRows(bond, rows, periodType);
    }

    private Map<LocalDate, BigDecimal> stockAdjustFactors(
            StockConfigPO stock,
            KlinePeriodTypeEnum periodType,
            Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "adj_factor",
                Map.of(
                        "ts_code", this.toTsCode(stock.getStockCode(), stock.getExchangeCode()),
                        "start_date", this.startDate(periodType, limit)),
                ADJ_FACTOR_FIELDS);
        Map<LocalDate, BigDecimal> factors = new HashMap<>();
        StreamSupport.stream(rows.spliterator(), false).forEach(row -> {
            String tradeDate = row.path("trade_date").asText(null);
            if (tradeDate == null || tradeDate.length() != 8 || row.path("adj_factor").isNull()) {
                return;
            }
            factors.put(this.date(tradeDate), new BigDecimal(row.path("adj_factor").asText()));
        });
        return factors;
    }

    private String stockApiName(KlinePeriodTypeEnum periodType) {
        if (KlinePeriodTypeEnum.WEEKLY.equals(periodType)) {
            return "weekly";
        }
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return "monthly";
        }
        return "daily";
    }

    private String indexApiName(KlinePeriodTypeEnum periodType) {
        if (KlinePeriodTypeEnum.WEEKLY.equals(periodType)) {
            return "index_weekly";
        }
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return "index_monthly";
        }
        return "index_daily";
    }

    private String startDate(KlinePeriodTypeEnum periodType, Integer limit) {
        int normalizedLimit = limit == null || limit < 1 ? 250 : limit;
        LocalDate today = LocalDate.now();
        if (KlinePeriodTypeEnum.MONTHLY.equals(periodType)) {
            return today.minusMonths(normalizedLimit).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if (KlinePeriodTypeEnum.WEEKLY.equals(periodType)) {
            return today.minusWeeks(normalizedLimit).format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        return today.minusDays(normalizedLimit * 2L).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private LocalDate date(String value) {
        return LocalDate.parse("%s-%s-%s".formatted(
                value.substring(0, 4),
                value.substring(4, 6),
                value.substring(6, 8)));
    }

    private String toTsCode(String code, String exchangeCode) {
        return code + "." + exchangeCode;
    }
}
