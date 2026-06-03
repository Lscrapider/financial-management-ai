package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record SceneAnalysisConfigFieldVO(
        String key,
        String label,
        List<String> path,
        Double defaultValue,
        Double min,
        Double max,
        Double step,
        String unit,
        String recommended,
        String description) {
}
