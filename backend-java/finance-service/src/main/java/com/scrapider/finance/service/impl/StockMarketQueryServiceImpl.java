package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockKlineParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockKlineVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockKlineManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.StockMarketQueryService;
import java.time.LocalDate;
import java.util.Comparator;
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

    public StockMarketQueryServiceImpl(
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage,
            StockKlineManage stockKlineManage) {
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
        this.stockKlineManage = stockKlineManage;
    }

    @Override
    public List<StockQuoteVO> listQuotes(StockQuoteListParam param) {
        return this.stockQuoteSnapshotManage
                .listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit(), DEFAULT_QUOTE_LIMIT),
                        StockQuoteSortFieldEnum.of(param.getSortField()),
                        SortOrderEnum.of(param.getSortOrder()))
                .stream()
                .map(StockQuoteVO::fromPO)
                .toList();
    }

    @Override
    public List<StockIntradayTrendVO> listIntradayTrends(StockIntradayTrendParam param) {
        if (StrUtil.isBlank(param.getStockCode())) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
        String stockCode = param.getStockCode().trim();
        String latestBatchNo = this.stockIntradayTrendInfluxManage.getLatestTodayBatchNo(stockCode);
        if (StrUtil.isBlank(latestBatchNo)) {
            latestBatchNo = this.stockIntradayTrendInfluxManage.getLatestBatchNo(stockCode);
        }
        if (StrUtil.isBlank(latestBatchNo)) {
            return List.of();
        }
        return this.stockIntradayTrendInfluxManage
                .listByBatchNo(stockCode, latestBatchNo)
                .stream()
                .map(StockIntradayTrendVO::fromPO)
                .toList();
    }

    @Override
    public List<StockKlineVO> listKlines(StockKlineParam param) {
        if (StrUtil.isBlank(param.getStockCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("stockCode or secid must not be blank");
        }
        return this.stockKlineManage
                .listKlines(
                        normalizeText(param.getStockCode()),
                        normalizeText(param.getSecid()),
                        normalizePeriodType(param.getPeriodType()),
                        normalizeAdjustType(param.getAdjustType()),
                        parseDate(param.getStartDate()),
                        parseDate(param.getEndDate()),
                        this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT))
                .stream()
                .map(StockKlineVO::fromPO)
                .sorted(Comparator.comparing(StockKlineVO::getTradeDate))
                .toList();
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit < 1) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static KlineAdjustTypeEnum normalizeAdjustType(String value) {
        if (StrUtil.isBlank(value)) {
            return KlineAdjustTypeEnum.HFQ;
        }
        String code = value.trim();
        for (KlineAdjustTypeEnum item : KlineAdjustTypeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return KlineAdjustTypeEnum.HFQ;
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

    private static String normalizeText(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private static LocalDate parseDate(String value) {
        return StrUtil.isBlank(value) ? null : LocalDate.parse(value.trim());
    }
}
