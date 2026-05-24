package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.AiDatabaseContextVO;
import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;

public interface AiMarketDataQueryService {

    AiDatabaseContextVO query(AiQueryRewriteVO queryRewrite);
}
