package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.dto.SceneAnalysisMessageDTO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisSubmitVO;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;

public final class SceneAnalysisTaskConverter {

    private SceneAnalysisTaskConverter() {
    }

    public static SceneAnalysisSubmitVO submitted(String taskNo, SceneAnalysisMessageDTO message) {
        return new SceneAnalysisSubmitVO(
                taskNo,
                message.target().type(),
                message.target().code(),
                message.config().profile(),
                SceneAnalysisTaskStatusEnum.PROCESSING_CURRENT_SCENES.getCode());
    }
}
