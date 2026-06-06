package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
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
import com.scrapider.finance.service.HistoricalKlineProvider;
import com.scrapider.finance.service.StockMarketQueryService;
import java.time.LocalDate;
import java.util.Arrays;
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
        return this.stockIntradayTrendInfluxManage
                .listLatestTradingTrends(stockCode)
                .stream()
                .map(StockIntradayTrendVO::fromPO)
                .toList();
    }

    @Override
    public List<StockKlineVO> listKlines(StockKlineParam param) {
        if (StrUtil.isBlank(param.getStockCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("stockCode or secid must not be blank");
        }
        String stockCode = normalizeText(param.getStockCode());
        String secid = normalizeText(param.getSecid());
        KlinePeriodTypeEnum periodType = normalizePeriodType(param.getPeriodType());
        KlineAdjustTypeEnum adjustType = normalizeAdjustType(param.getAdjustType());
        LocalDate startDate = parseDate(param.getStartDate());
        LocalDate endDate = parseDate(param.getEndDate());
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
        return klines
                .stream()
                .map(StockKlineVO::fromPO)
                .sorted(Comparator.comparing(StockKlineVO::getTradeDate))
                .toList();
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
