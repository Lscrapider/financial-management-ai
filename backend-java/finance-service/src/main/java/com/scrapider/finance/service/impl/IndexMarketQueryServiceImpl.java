package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.param.IndexDailyKlineParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.vo.IndexDailyKlineVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import com.scrapider.finance.manage.IndexDailyKlineManage;
import com.scrapider.finance.manage.IndexQuoteSnapshotManage;
import com.scrapider.finance.service.IndexMarketQueryService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndexMarketQueryServiceImpl implements IndexMarketQueryService {

    private static final int DEFAULT_QUOTE_LIMIT = 100;
    private static final int DEFAULT_KLINE_LIMIT = 250;
    private static final int MAX_LIMIT = 500;

    private final IndexQuoteSnapshotManage indexQuoteSnapshotManage;
    private final IndexDailyKlineManage indexDailyKlineManage;

    public IndexMarketQueryServiceImpl(
            IndexQuoteSnapshotManage indexQuoteSnapshotManage,
            IndexDailyKlineManage indexDailyKlineManage) {
        this.indexQuoteSnapshotManage = indexQuoteSnapshotManage;
        this.indexDailyKlineManage = indexDailyKlineManage;
    }

    @Override
    public List<IndexQuoteVO> listQuotes(IndexQuoteListParam param) {
        return this.indexQuoteSnapshotManage
                .listSnapshots(
                        param.getMarketCode(),
                        this.normalizeLimit(param.getLimit(), DEFAULT_QUOTE_LIMIT),
                        IndexQuoteSortFieldEnum.of(param.getSortField()),
                        SortOrderEnum.of(param.getSortOrder()))
                .stream()
                .map(IndexQuoteVO::fromPO)
                .toList();
    }

    @Override
    public List<IndexDailyKlineVO> listDailyKlines(IndexDailyKlineParam param) {
        if (StrUtil.isBlank(param.getIndexCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("indexCode or secid must not be blank");
        }
        return this.indexDailyKlineManage
                .listDailyKlines(
                        normalizeText(param.getIndexCode()),
                        normalizeText(param.getSecid()),
                        parseDate(param.getStartDate()),
                        parseDate(param.getEndDate()),
                        this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT))
                .stream()
                .map(IndexDailyKlineVO::fromPO)
                .sorted(Comparator.comparing(IndexDailyKlineVO::getTradeDate))
                .toList();
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

    private static LocalDate parseDate(String value) {
        return StrUtil.isBlank(value) ? null : LocalDate.parse(value.trim());
    }
}
