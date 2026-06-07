package com.scrapider.finance.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.MarketQueryConverter;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.param.IndexKlineParam;
import com.scrapider.finance.domain.param.IndexIntradayTrendParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.domain.vo.IndexKlineVO;
import com.scrapider.finance.domain.vo.IndexIntradayTrendVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import com.scrapider.finance.manage.IndexConfigManage;
import com.scrapider.finance.manage.IndexIntradayTrendInfluxManage;
import com.scrapider.finance.manage.IndexKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.service.HistoricalKlineProvider;
import com.scrapider.finance.service.IndexMarketQueryService;
import com.scrapider.finance.task.IndexMarketSyncTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndexMarketQueryServiceImpl implements IndexMarketQueryService {

    private static final int DEFAULT_QUOTE_LIMIT = 100;
    private static final int DEFAULT_KLINE_LIMIT = 250;
    private static final int MAX_LIMIT = 500;

    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage;
    private final IndexKlineManage indexKlineManage;
    private final IndexConfigManage indexConfigManage;
    private final HistoricalKlineProvider historicalKlineProvider;
    private final IndexMarketSyncTask indexMarketSyncTask;

    public IndexMarketQueryServiceImpl(
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexIntradayTrendInfluxManage indexIntradayTrendInfluxManage,
            IndexKlineManage indexKlineManage,
            IndexConfigManage indexConfigManage,
            HistoricalKlineProvider historicalKlineProvider,
            IndexMarketSyncTask indexMarketSyncTask) {
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexIntradayTrendInfluxManage = indexIntradayTrendInfluxManage;
        this.indexKlineManage = indexKlineManage;
        this.indexConfigManage = indexConfigManage;
        this.historicalKlineProvider = historicalKlineProvider;
        this.indexMarketSyncTask = indexMarketSyncTask;
    }

    @Override
    public List<IndexQuoteVO> listQuotes(IndexQuoteListParam param) {
        return MarketQueryConverter.toIndexQuoteVOList(
                this.indexQuoteSnapshotManage.listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit(), DEFAULT_QUOTE_LIMIT),
                        IndexQuoteSortFieldEnum.of(param.getSortField()),
                        SortOrderEnum.of(param.getSortOrder())));
    }

    @Override
    public List<IndexIntradayTrendVO> listIntradayTrends(IndexIntradayTrendParam param) {
        if (StrUtil.isBlank(param.getIndexCode())) {
            throw new IllegalArgumentException("indexCode must not be blank");
        }
        IndexConfigPO index = this.indexConfigManage.getEnabledByIndexCode(param.getIndexCode());
        if (index == null || StrUtil.isBlank(index.getSecid())) {
            return List.of();
        }
        List<IndexIntradayTrendVO> trends = this.listIntradayTrendVOs(index.getIndexCode());
        if (trends.isEmpty()) {
            this.indexMarketSyncTask.syncTrendForIndex(index.getIndexCode());
            trends = this.listIntradayTrendVOs(index.getIndexCode());
        }
        return trends;
    }

    @Override
    public List<IndexKlineVO> listKlines(IndexKlineParam param) {
        if (StrUtil.isBlank(param.getIndexCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("indexCode or secid must not be blank");
        }
        String indexCode = StrUtil.trimToNull(param.getIndexCode());
        String secid = StrUtil.trimToNull(param.getSecid());
        KlinePeriodTypeEnum periodType = this.normalizePeriodType(param.getPeriodType());
        LocalDate startDate = this.parseDate(param.getStartDate());
        LocalDate endDate = this.parseDate(param.getEndDate());
        int limit = this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT);
        List<IndexKlinePO> klines = this.listKlinePOs(
                indexCode,
                secid,
                periodType,
                startDate,
                endDate,
                limit);
        if (klines.isEmpty() && StrUtil.isNotBlank(indexCode)) {
            this.syncKlines(indexCode, periodType, limit);
            klines = this.listKlinePOs(indexCode, secid, periodType, startDate, endDate, limit);
        }
        return MarketQueryConverter.toIndexKlineVOList(klines);
    }

    private List<IndexKlinePO> listKlinePOs(
            String indexCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        return this.indexKlineManage.listKlines(
                indexCode,
                secid,
                periodType,
                startDate,
                endDate,
                limit);
    }

    private List<IndexIntradayTrendVO> listIntradayTrendVOs(String indexCode) {
        return MarketQueryConverter.toIndexIntradayTrendVOList(
                this.indexIntradayTrendInfluxManage.listLatestTradingTrends(indexCode));
    }

    private void syncKlines(String indexCode, KlinePeriodTypeEnum periodType, Integer limit) {
        IndexConfigPO index = this.indexConfigManage.getEnabledByIndexCode(indexCode);
        if (index == null || StrUtil.isBlank(index.getSecid())) {
            return;
        }
        this.indexKlineManage.saveKlines(this.historicalKlineProvider.getIndexKlines(index, periodType, limit));
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private KlinePeriodTypeEnum normalizePeriodType(String value) {
        if (StrUtil.isBlank(value)) {
            return KlinePeriodTypeEnum.DAILY;
        }
        String code = StrUtil.trim(value);
        return EnumUtil.getBy(KlinePeriodTypeEnum.class, KlinePeriodTypeEnum::getCode, code, KlinePeriodTypeEnum.DAILY);
    }

    private LocalDate parseDate(String value) {
        String date = StrUtil.trimToNull(value);
        return date == null ? null : LocalDateTimeUtil.parseDate(date);
    }
}
