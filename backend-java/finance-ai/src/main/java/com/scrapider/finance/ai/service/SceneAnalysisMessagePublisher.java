package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;

public interface SceneAnalysisMessagePublisher {

    void publishCurrentSceneAnalysisMessage(SceneAnalysisMessageDTO message);
}
