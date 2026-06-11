package com.scrapider.finance.provider.impl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scrapider.finance.provider.ConvertibleBondSceneDataProvider;
import com.scrapider.finance.api.TushareApi;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.provider.ConvertibleBondDataProvider;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.provider.convertible-bond", havingValue = "tushare")
public class TushareConvertibleBondDataProvider implements ConvertibleBondDataProvider, ConvertibleBondSceneDataProvider {

    private static final String CB_BASIC_FIELDS =
            "ts_code,bond_short_name,stk_code,stk_short_name,issue_size,remain_size,"
                    + "value_date,maturity_date,coupon_rate,pay_per_year,maturity_call_price,"
                    + "first_conv_price,conv_price,conv_start_date,conv_end_date,call_clause,"
                    + "put_clause,reset_clause,issue_rating,newest_rating";
    private static final String CB_DAILY_FIELDS =
            "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount,"
                    + "bond_value,bond_over_rate,cb_value,cb_over_rate";
    private static final String CB_SHARE_FIELDS =
            "ts_code,end_date,issue_size,convert_price,convert_val,convert_vol,convert_ratio,remain_size";

    private final TushareApi tushareApi;

    public TushareConvertibleBondDataProvider(TushareApi tushareApi) {
        this.tushareApi = tushareApi;
    }

    @Override
    public ConvertibleBondBasicPO getBasic(BondConfigPO bond) {
        ArrayNode rows = this.tushareApi.queryRows(
                "cb_basic",
                Map.of("ts_code", this.toTsCode(bond)),
                CB_BASIC_FIELDS);
        return ConvertibleBondBasicPO.fromTushareRows(bond, rows);
    }

    @Override
    public List<ConvertibleBondDailyValuationPO> getDailyValuations(BondConfigPO bond, Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "cb_daily",
                Map.of("ts_code", this.toTsCode(bond), "start_date", this.startDate(limit)),
                CB_DAILY_FIELDS);
        return ConvertibleBondDailyValuationPO.fromTushareRows(bond, rows);
    }

    @Override
    public List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit) {
        ArrayNode rows = this.tushareApi.queryRows(
                "cb_share",
                Map.of("ts_code", this.toTsCode(bond)),
                CB_SHARE_FIELDS);
        return ConvertibleBondSharePO.fromTushareRows(bond, rows);
    }

    private int limit(Integer limit) {
        return limit == null || limit < 1 ? 250 : limit;
    }

    private String startDate(Integer limit) {
        return java.time.LocalDate.now()
                .minusDays(this.limit(limit) * 2L)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String toTsCode(BondConfigPO bond) {
        return bond.getBondCode() + "." + bond.getExchangeCode();
    }
}
