package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.AiConsoleOverviewVO;
import com.scrapider.finance.ai.domain.vo.AppVisitTrendVO;
import java.util.List;

public interface AiConsoleMetricsService {

    AiConsoleOverviewVO overview(Integer days);

    List<AppVisitTrendVO> visitTrends(Integer hours);
}
