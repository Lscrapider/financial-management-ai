package com.scrapider.finance.ai.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import java.time.LocalDateTime;

public record SceneAnalysisConfigProfileVO(
        Long id,
        String name,
        String configGroup,
        String configProfile,
        String targetType,
        String reportType,
        JsonNode configJson,
        Boolean systemDefault,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SceneAnalysisConfigProfileVO fromPO(SceneAnalysisConfigProfilePO po) {
        return new SceneAnalysisConfigProfileVO(
                po.getId(),
                po.getName(),
                po.getConfigGroup(),
                po.getConfigProfile(),
                po.getTargetType(),
                po.getReportType(),
                po.getConfigJson(),
                po.getSystemDefault(),
                po.getEnabled(),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }
}
