package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCurrentScenesPayloadParam;

public interface SceneReportPipelineService {

    void start(String taskNo, SceneAnalysisCurrentScenesPayloadParam currentScenesPayload);
}
