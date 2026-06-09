package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import java.util.List;

public interface SceneAnalysisReportService {

    void generateAfterKnowledgeRetrieved(String taskNo);

    void regenerateFromStoredContext(String taskNo);

    SceneAnalysisReportVO getReport(String taskNo);

    SceneAnalysisReportTargetPageVO pageTargets(
            int pageNum,
            int pageSize,
            String targetName,
            String targetCode,
            String targetType);

    List<SceneAnalysisReportHistoryVO> listHistory(String targetType, String targetCode);

    SceneAnalysisReportDetailVO detail(Long reportId);
}
