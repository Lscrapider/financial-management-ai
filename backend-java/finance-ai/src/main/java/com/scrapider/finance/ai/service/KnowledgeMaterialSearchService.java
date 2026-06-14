package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.KnowledgeMaterialSearchParam;
import com.scrapider.finance.ai.domain.param.SceneAnalysisCallbackParam;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialSubmitVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeMaterialTaskVO;

public interface KnowledgeMaterialSearchService {

    KnowledgeMaterialSubmitVO submit(KnowledgeMaterialSearchParam param);

    void callback(String taskNo, String callbackToken, SceneAnalysisCallbackParam param);

    KnowledgeMaterialTaskVO detail(String taskNo);
}
