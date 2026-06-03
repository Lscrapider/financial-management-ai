package com.scrapider.finance.ai.domain.param;

import java.util.List;
import java.util.Map;

public record SceneAnalysisSceneModuleParam(
        Double score,
        String level,
        String direction,
        Map<String, Double> tags,
        List<String> evidence,
        String queryText) {
}
