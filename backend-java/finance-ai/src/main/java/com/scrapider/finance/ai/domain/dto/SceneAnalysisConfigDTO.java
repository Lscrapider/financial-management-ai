package com.scrapider.finance.ai.domain.dto;

import com.scrapider.finance.ai.domain.param.SceneAnalysisUserConfigParam;

public record SceneAnalysisConfigDTO(
        String profile,
        SceneAnalysisUserConfigParam parameters) {
}
