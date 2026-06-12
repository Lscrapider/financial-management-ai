package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisSubmitParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;

public interface SceneAnalysisTaskService {

    SceneAnalysisSubmitVO submit(SceneAnalysisSubmitParam param);

    void callback(String taskNo, String callbackToken, SceneAnalysisCallbackParam param);
}
