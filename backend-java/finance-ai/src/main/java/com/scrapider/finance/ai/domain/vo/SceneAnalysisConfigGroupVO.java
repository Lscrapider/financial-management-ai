package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record SceneAnalysisConfigGroupVO(
        String name,
        String label,
        List<SceneAnalysisConfigFieldVO> fields) {
}
