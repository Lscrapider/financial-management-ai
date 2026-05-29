package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import com.scrapider.finance.manage.StockIntradayTrendInfluxManage;
import com.scrapider.finance.manage.StockQuoteSnapshotManage;
import com.scrapider.finance.service.StockMarketQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockMarketQueryServiceImpl implements StockMarketQueryService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final StockQuoteSnapshotManage stockQuoteSnapshotManage;
    private final StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage;

    public StockMarketQueryServiceImpl(
            StockQuoteSnapshotManage stockQuoteSnapshotManage,
            StockIntradayTrendInfluxManage stockIntradayTrendInfluxManage) {
        this.stockQuoteSnapshotManage = stockQuoteSnapshotManage;
        this.stockIntradayTrendInfluxManage = stockIntradayTrendInfluxManage;
    }

    @Override
    public List<StockQuoteVO> listQuotes(StockQuoteListParam param) {
        return this.stockQuoteSnapshotManage
                .listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit()),
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
