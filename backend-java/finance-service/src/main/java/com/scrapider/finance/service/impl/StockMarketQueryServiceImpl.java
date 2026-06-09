package com.scrapider.finance.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.converter.MarketQueryConverter;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockKlineParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockKlineVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.manage.StockConfigManage;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.provider.HistoricalKlineProvider;
import com.scrapider.finance.service.StockMarketQueryService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockMarketQueryServiceImpl implements StockMarketQueryService {

    private static final int DEFAULT_KLINE_LIMIT = 250;
    private static final int DEFAULT_QUOTE_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;
    private final StockKlineManage stockKlineManage;
    private final StockConfigManage stockConfigManage;
    private final HistoricalKlineProvider historicalKlineProvider;

    public StockMarketQueryServiceImpl(
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockKlineManage stockKlineManage,
            StockConfigManage stockConfigManage,
            HistoricalKlineProvider historicalKlineProvider) {
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.stockKlineManage = stockKlineManage;
        this.stockConfigManage = stockConfigManage;
        this.historicalKlineProvider = historicalKlineProvider;
    }

    @Override
    public List<StockQuoteVO> listQuotes(StockQuoteListParam param) {
        return MarketQueryConverter.toStockQuoteVOList(
                this.stockQuoteSnapshotManage.listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit(), DEFAULT_QUOTE_LIMIT),
                        StockQuoteSortFieldEnum.of(param.getSortField()),
                        SortOrderEnum.of(param.getSortOrder())));
    }

    @Override
    public List<StockIntradayTrendVO> listIntradayTrends(StockIntradayTrendParam param) {
        if (StrUtil.isBlank(param.getStockCode())) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
        String stockCode = StrUtil.trim(param.getStockCode());
        return MarketQueryConverter.toStockIntradayTrendVOList(
                this.stockIntradayTrendInfluxManage.listLatestTradingTrends(stockCode));
    }

    @Override
    public List<StockKlineVO> listKlines(StockKlineParam param) {
        if (StrUtil.isBlank(param.getStockCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("stockCode or secid must not be blank");
        }
        String stockCode = StrUtil.trimToNull(param.getStockCode());
        String secid = StrUtil.trimToNull(param.getSecid());
        KlinePeriodTypeEnum periodType = this.normalizePeriodType(param.getPeriodType());
        KlineAdjustTypeEnum adjustType = this.normalizeAdjustType(param.getAdjustType());
        LocalDate startDate = this.parseDate(param.getStartDate());
        LocalDate endDate = this.parseDate(param.getEndDate());
        int limit = this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT);
        List<StockKlinePO> klines = this.listKlinePOs(
                stockCode,
                secid,
                periodType,
                adjustType,
                startDate,
                endDate,
                limit);
        if (klines.isEmpty() && StrUtil.isNotBlank(stockCode)) {
            this.syncKlines(stockCode, periodType, adjustType, limit);
            klines = this.listKlinePOs(
                    stockCode,
                    secid,
                    periodType,
                    adjustType,
                    startDate,
                    endDate,
                    limit);
        }
        return MarketQueryConverter.toStockKlineVOList(klines);
    }

    private List<StockKlinePO> listKlinePOs(
            String stockCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        return this.stockKlineManage.listKlines(
                stockCode,
                secid,
                periodType,
                adjustType,
                startDate,
                endDate,
                limit);
    }

    private void syncKlines(
            String stockCode,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            Integer limit) {
        StockConfigPO stock = this.stockConfigManage.getEnabledByStockCode(stockCode);
        if (stock == null || StrUtil.isBlank(stock.getSecid())) {
            return;
        }
        Arrays.stream(KlineAdjustTypeEnum.values())
                .map(item -> this.historicalKlineProvider.getStockKlines(stock, periodType, item, limit))
                .forEach(this.stockKlineManage::saveKlines);
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private KlineAdjustTypeEnum normalizeAdjustType(String value) {
        if (StrUtil.isBlank(value)) {
            return KlineAdjustTypeEnum.HFQ;
        }
        String code = StrUtil.trim(value);
        return EnumUtil.getBy(KlineAdjustTypeEnum.class, KlineAdjustTypeEnum::getCode, code, KlineAdjustTypeEnum.HFQ);
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
