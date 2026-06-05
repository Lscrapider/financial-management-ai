package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.IndexKlineParam;
import com.scrapider.finance.domain.param.IndexIntradayTrendParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.vo.IndexKlineVO;
import com.scrapider.finance.domain.vo.IndexIntradayTrendVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import java.util.List;

public interface IndexMarketQueryService {

    List<IndexQuoteVO> listQuotes(IndexQuoteListParam param);

    List<IndexIntradayTrendVO> listIntradayTrends(IndexIntradayTrendParam param);

    List<IndexKlineVO> listKlines(IndexKlineParam param);
}
