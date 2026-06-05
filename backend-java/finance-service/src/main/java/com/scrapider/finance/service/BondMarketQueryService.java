package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.BondKlineParam;
import com.scrapider.finance.domain.param.BondIntradayTrendParam;
import com.scrapider.finance.domain.param.BondQuoteListParam;
import com.scrapider.finance.domain.vo.BondKlineVO;
import com.scrapider.finance.domain.vo.BondIntradayTrendVO;
import com.scrapider.finance.domain.vo.BondQuoteVO;
import java.util.List;

public interface BondMarketQueryService {

    List<BondQuoteVO> listQuotes(BondQuoteListParam param);

    List<BondIntradayTrendVO> listIntradayTrends(BondIntradayTrendParam param);

    List<BondKlineVO> listKlines(BondKlineParam param);
}
