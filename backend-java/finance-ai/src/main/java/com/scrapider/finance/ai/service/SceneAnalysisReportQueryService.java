package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import java.util.List;

public interface SceneAnalysisReportQueryService {

    SceneAnalysisReportTargetPageVO pageTargets(int pageNum, int pageSize, String keyword);

    List<SceneAnalysisReportHistoryVO> listHistory(String targetType, String targetCode);

    SceneAnalysisReportDetailVO detail(Long reportId);
}
