package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.StockIntradayTrendParam;
import com.scrapider.finance.domain.param.StockKlineParam;
import com.scrapider.finance.domain.param.StockQuoteListParam;
import com.scrapider.finance.domain.vo.StockIntradayTrendVO;
import com.scrapider.finance.domain.vo.StockKlineVO;
import com.scrapider.finance.domain.vo.StockQuoteVO;
import java.util.List;

public interface StockMarketQueryService {

    List<StockQuoteVO> listQuotes(StockQuoteListParam param);

    List<StockIntradayTrendVO> listIntradayTrends(StockIntradayTrendParam param);

    List<StockKlineVO> listKlines(StockKlineParam param);
}
