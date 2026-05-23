package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.IndexDailyKlineParam;
import com.scrapider.finance.domain.param.IndexQuoteListParam;
import com.scrapider.finance.domain.vo.IndexDailyKlineVO;
import com.scrapider.finance.domain.vo.IndexQuoteVO;
import java.util.List;

public interface IndexMarketQueryService {

    List<IndexQuoteVO> listQuotes(IndexQuoteListParam param);

    List<IndexDailyKlineVO> listDailyKlines(IndexDailyKlineParam param);
}
