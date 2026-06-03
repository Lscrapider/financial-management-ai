package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;

public interface SceneAnalysisReportGenerationService {

    void generateAfterKnowledgeRetrieved(String taskNo);

    void regenerateFromStoredContext(String taskNo);

    SceneAnalysisReportVO getReport(String taskNo);
}
