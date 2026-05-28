package com.scrapider.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.enums.BondQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.param.BondDailyKlineParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.vo.BondDailyKlineVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import com.scrapider.finance.manage.BondDailyKlineManage;
import com.scrapider.finance.manage.BondQuoteSnapshotManage;
import com.scrapider.finance.service.BondMarketQueryService;
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
    private final BondDailyKlineManage bondDailyKlineManage;

    public BondMarketQueryServiceImpl(
            BondQuoteSnapshotManage bondQuoteSnapshotManage,
            BondDailyKlineManage bondDailyKlineManage) {
        this.bondQuoteSnapshotManage = bondQuoteSnapshotManage;
        this.bondDailyKlineManage = bondDailyKlineManage;
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
    public List<BondDailyKlineVO> listDailyKlines(BondDailyKlineParam param) {
        if (StrUtil.isBlank(param.getBondCode()) && StrUtil.isBlank(param.getSecid())) {
            throw new IllegalArgumentException("bondCode or secid must not be blank");
        }
        return this.bondDailyKlineManage
                .listDailyKlines(
                        normalizeText(param.getBondCode()),
                        normalizeText(param.getSecid()),
                        parseDate(param.getStartDate()),
                        parseDate(param.getEndDate()),
                        this.normalizeLimit(param.getLimit(), DEFAULT_KLINE_LIMIT))
                .stream()
                .map(BondDailyKlineVO::fromPO)
                .sorted(Comparator.comparing(BondDailyKlineVO::getTradeDate))
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
