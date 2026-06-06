package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.BondQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.param.BondKlineParam;
import com.scrapider.finance.domain.param.BondIntradayTrendParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.domain.vo.BondKlineVO;
import com.scrapider.finance.domain.vo.BondIntradayTrendVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import com.scrapider.finance.manage.BondConfigManage;
import com.scrapider.finance.manage.BondIntradayTrendInfluxManage;
import com.scrapider.finance.manage.BondKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.service.BondMarketQueryService;
import com.scrapider.finance.service.HistoricalKlineProvider;
import com.scrapider.finance.task.BondMarketSyncTask;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondMarketQueryServiceImpl implements BondMarketQueryService {

    private static final int DEFAULT_QUOTE_LIMIT = 100;
    private static final int DEFAULT_KLINE_LIMIT = 250;
    private static final int MAX_LIMIT = 500;

    private final BondQuoteSnapshotManage bondQuoteSnapshotManage;
    private final BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage;
    private final BondKlineManage bondKlineManage;
    private final BondConfigManage bondConfigManage;
    private final HistoricalKlineProvider historicalKlineProvider;
    private final BondMarketSyncTask bondMarketSyncTask;

    public BondMarketQueryServiceImpl(
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondIntradayTrendInfluxManage bondIntradayTrendInfluxManage,
            BondKlineManage bondKlineManage,
            BondConfigManage bondConfigManage,
            HistoricalKlineProvider historicalKlineProvider,
            BondMarketSyncTask bondMarketSyncTask) {
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondIntradayTrendInfluxManage = bondIntradayTrendInfluxManage;
        this.bondKlineManage = bondKlineManage;
        this.bondConfigManage = bondConfigManage;
        this.historicalKlineProvider = historicalKlineProvider;
        this.bondMarketSyncTask = bondMarketSyncTask;
    }

    @Override
    public List<BondQuoteVO> listQuotes(BondQuoteListParam param) {
        return this.bondQuoteSnapshotManage
                .listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit(), DEFAULT_QUOTE_LIMIT),
                        BondQuoteSortFieldEnum.of(param.getSortField()),
                        SortOrderEnum.of(param.getSortOrder()))
                .stream()
                .map(BondQuoteVO::fromPO)
                .toList();
    }

    @Override
    public List<BondIntradayTrendVO> listIntradayTrends(BondIntradayTrendParam param) {
        if (StrUtil.isBlank(param.getBondCode())) {
            throw new IllegalArgumentException("bondCode must not be blank");
        }
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(param.getBondCode());
        if (bond == null || StrUtil.isBlank(bond.getSecid())) {
            return List.of();
        }
        List<BondIntradayTrendVO> trends = this.listIntradayTrendVOs(bond.getBondCode());
        if (trends.isEmpty()) {
            this.bondMarketSyncTask.syncTrendForBond(bond.getBondCode());
            trends = this.listIntradayTrendVOs(bond.getBondCode());
        }
        return trends;
    }

    @Override
    public List<BondKlineVO> listKlines(BondKlineParam param) {
        if (StrUtil.isBlank(param.getBondCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("bondCode or secid must not be blank");
        }
        String bondCode = normalizeText(param.getBondCode());
        String secid = normalizeText(param.getSecid());
        KlinePeriodTypeEnum periodType = normalizePeriodType(param.getPeriodType());
        LocalDate startDate = parseDate(param.getStartDate());
        LocalDate endDate = parseDate(param.getEndDate());
        int limit = this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT);
        List<BondKlinePO> klines = this.listKlinePOs(
                bondCode,
                secid,
                periodType,
                startDate,
                endDate,
                limit);
        if (klines.isEmpty() && StrUtil.isNotBlank(bondCode)) {
            this.syncKlines(bondCode, periodType, limit);
            klines = this.listKlinePOs(bondCode, secid, periodType, startDate, endDate, limit);
        }
        return klines
                .stream()
                .map(BondKlineVO::fromPO)
                .sorted(Comparator.comparing(BondKlineVO::getTradeDate))
                .toList();
    }

    private List<BondKlinePO> listKlinePOs(
            String bondCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        return this.bondKlineManage.listKlines(
                bondCode,
                secid,
                periodType,
                startDate,
                endDate,
                limit);
    }

    private List<BondIntradayTrendVO> listIntradayTrendVOs(String bondCode) {
        return this.bondIntradayTrendInfluxManage
                .listLatestTradingTrends(bondCode)
                .stream()
                .map(BondIntradayTrendVO::fromPO)
                .toList();
    }

    private void syncKlines(String bondCode, KlinePeriodTypeEnum periodType, Integer limit) {
        BondConfigPO bond = this.bondConfigManage.getEnabledByBondCode(bondCode);
        if (bond == null || StrUtil.isBlank(bond.getSecid())) {
            return;
        }
        this.bondKlineManage.saveKlines(this.historicalKlineProvider.getBondKlines(bond, periodType, limit));
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeText(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private static KlinePeriodTypeEnum normalizePeriodType(String value) {
        if (StrUtil.isBlank(value)) {
            return KlinePeriodTypeEnum.DAILY;
        }
        String code = value.trim();
        for (KlinePeriodTypeEnum item : KlinePeriodTypeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return KlinePeriodTypeEnum.DAILY;
    }

    private static LocalDate parseDate(String value) {
        return StrUtil.isBlank(value) ? null : LocalDate.parse(value.trim());
    }
}
