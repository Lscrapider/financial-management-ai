package com.scrapider.finance.ai.converter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigFieldVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigGroupVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigProfileVO;
import com.scrapider.finance.domain.po.SceneAnalysisConfigProfilePO;
import java.util.List;

public final class SceneAnalysisConfigConverter {

    private SceneAnalysisConfigConverter() {
    }

    public static SceneAnalysisConfigGroupVO group(
            String name,
            String label,
            List<SceneAnalysisConfigFieldVO> fields) {
        return new SceneAnalysisConfigGroupVO(name, label, fields);
    }

    public static SceneAnalysisConfigFieldVO field(
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
        return new SceneAnalysisConfigFieldVO(
                key,
                label,
                path,
                defaultValue,
                min,
                max,
                step,
                unit,
                recommended,
                description);
    }

    public static ObjectNode normalizedConfigJson(
            JsonNode configJson,
            String reportType,
            int totalChunks,
            String configProfile,
            String targetType) {
        ObjectNode normalizedJson = configJson.deepCopy();
        normalizedJson.put("reportType", reportType);
        normalizedJson.put("totalChunks", totalChunks);
        normalizedJson.put("configProfile", configProfile);
        if (StrUtil.isNotBlank(targetType)) {
            normalizedJson.put("targetType", targetType);
        } else {
            normalizedJson.remove("targetType");
        }
        if (!normalizedJson.has("userOverrides") || normalizedJson.path("userOverrides").isNull()) {
            normalizedJson.set("userOverrides", JsonNodeFactory.instance.objectNode());
        }
        return normalizedJson;
    }

    public static List<SceneAnalysisConfigProfileVO> toProfileVOList(List<SceneAnalysisConfigProfilePO> profiles) {
        return profiles.stream()
                .map(SceneAnalysisConfigProfileVO::fromPO)
                .toList();
    }
}
