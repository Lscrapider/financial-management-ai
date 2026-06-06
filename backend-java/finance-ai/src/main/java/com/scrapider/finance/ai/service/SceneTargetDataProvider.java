package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;

public interface SceneTargetDataProvider {

    boolean supports(String targetType);

    SceneAnalysisMessageDTO buildMessage(String taskNo, String targetCode, SceneAnalysisSubmitParam param);
}
