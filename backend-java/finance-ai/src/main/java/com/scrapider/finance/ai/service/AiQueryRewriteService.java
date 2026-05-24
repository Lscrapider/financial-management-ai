package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.AiQueryRewriteVO;

public interface AiQueryRewriteService {

    AiQueryRewriteVO rewrite(String message);
}
