package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.BondDailyKlineParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.vo.BondDailyKlineVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import java.util.List;

public interface BondMarketQueryService {

    List<BondQuoteVO> listQuotes(BondQuoteListParam param);

    List<BondDailyKlineVO> listDailyKlines(BondDailyKlineParam param);
}
