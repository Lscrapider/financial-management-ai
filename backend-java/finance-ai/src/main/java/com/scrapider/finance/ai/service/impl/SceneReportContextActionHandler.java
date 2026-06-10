package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.ai.converter.AiAgentDomainToolDataConverter;
import com.scrapider.finance.ai.domain.dto.AgentSessionDTO;
import com.scrapider.finance.ai.domain.param.AgentDataQueryParam;
import com.scrapider.finance.ai.domain.vo.AgentDataGatewayResponseVO;
import com.scrapider.finance.ai.service.AgentDataActionHandler;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SceneReportContextActionHandler implements AgentDataActionHandler {

    public static final String ACTION = "scene_report.context";

    private static final int DEFAULT_LIMIT = 1;
    private static final int MAX_LIMIT = 3;
    private static final int DEFAULT_REPORT_TEXT_MAX_CHARS = 12000;
    private static final int MAX_REPORT_TEXT_MAX_CHARS = 30000;

    private final SceneAnalysisReportManage sceneAnalysisReportManage;

    public SceneReportContextActionHandler(SceneAnalysisReportManage sceneAnalysisReportManage) {
        this.sceneAnalysisReportManage = sceneAnalysisReportManage;
    }

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public String runningMessage(AgentDataQueryParam param) {
        return "正在读取历史分析报告";
    }

    @Override
    public AgentDataGatewayResponseVO handle(AgentSessionDTO session, AgentDataQueryParam param) {
        JsonNode params = param.params();
        Long reportId = this.longParam(params, "reportId");
        String targetType = this.normalizeTargetType(this.textParam(params, "targetType"));
        String targetCode = this.textParam(params, "targetCode");
        int limit = this.normalizeLimit(params);
        int reportTextMaxChars = this.normalizeReportTextMaxChars(params);

        List<SceneAnalysisReportPO> reports = List.of();
        List<SceneAnalysisReportHistoryDTO> reportSummaries = List.of();
        List<Map<String, Object>> dataRows;
        String missingReason = null;
        String mode;
        if (reportId != null) {
            SceneAnalysisReportPO report = this.sceneAnalysisReportManage.findByIdForOwner(reportId, session.userId());
            reports = report == null ? List.of() : List.of(report);
            if (report == null) {
                missingReason = "report";
            }
            dataRows = AiAgentDomainToolDataConverter.sceneReports(reports, reportTextMaxChars);
            mode = "detail";
        } else if (StrUtil.isNotBlank(targetType) && StrUtil.isNotBlank(targetCode)) {
            reports = this.sceneAnalysisReportManage.listLatestSuccessByTarget(
                    targetType,
                    targetCode,
                    session.userId(),
                    limit);
            if (reports.isEmpty()) {
                missingReason = "report";
            }
            dataRows = AiAgentDomainToolDataConverter.sceneReports(reports, reportTextMaxChars);
            mode = "target_reports";
        } else {
            reportSummaries = this.sceneAnalysisReportManage.listLatestSuccessSummaries(
                    targetType,
                    session.userId(),
                    limit);
            if (reportSummaries.isEmpty()) {
                missingReason = "report";
            }
            dataRows = AiAgentDomainToolDataConverter.sceneReportSummaries(reportSummaries);
            mode = "latest_list";
        }

        Map<String, Object> dataCompleteness = new LinkedHashMap<>();
        dataCompleteness.put("complete", missingReason == null);
        dataCompleteness.put("missingReason", missingReason);
        dataCompleteness.put("reportCount", dataRows.size());
        dataCompleteness.put("reportTextMaxChars", reportTextMaxChars);
        dataCompleteness.put("reportTextTruncated", reports.stream()
                .anyMatch(report -> AiAgentDomainToolDataConverter.isReportTextTruncated(report, reportTextMaxChars)));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queriedAt", OffsetDateTime.now().toString());
        metadata.put("mode", mode);
        metadata.put("reportId", reportId);
        metadata.put("targetType", targetType);
        metadata.put("targetCode", targetCode);
        metadata.put("limit", limit);
        metadata.put("reportTextMaxChars", reportTextMaxChars);
        metadata.put("dataCompleteness", dataCompleteness);

        return new AgentDataGatewayResponseVO(
                param.action(),
                true,
                dataRows,
                metadata,
                null);
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return null;
        }
        String normalized = targetType.trim().toUpperCase();
        return switch (normalized) {
            case "STOCK", "股票" -> "STOCK";
            case "BOND", "CONVERTIBLE_BOND", "CONVERTIBLE_BOND_CN", "可转债" -> "CONVERTIBLE_BOND";
            case "INDEX", "指数" -> "INDEX";
            default -> normalized;
        };
    }

    private int normalizeLimit(JsonNode params) {
        if (params == null || params.isNull() || !params.has("limit")) {
            return DEFAULT_LIMIT;
        }
        int limit = params.get("limit").asInt(DEFAULT_LIMIT);
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeReportTextMaxChars(JsonNode params) {
        if (params == null || params.isNull() || !params.has("reportTextMaxChars")) {
            return DEFAULT_REPORT_TEXT_MAX_CHARS;
        }
        int limit = params.get("reportTextMaxChars").asInt(DEFAULT_REPORT_TEXT_MAX_CHARS);
        if (limit <= 0) {
            return DEFAULT_REPORT_TEXT_MAX_CHARS;
        }
        return Math.min(limit, MAX_REPORT_TEXT_MAX_CHARS);
    }

    private Long longParam(JsonNode params, String fieldName) {
        if (params == null || params.isNull()) {
            return null;
        }
        JsonNode value = params.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String textParam(JsonNode params, String fieldName) {
        if (params == null || params.isNull()) {
            return null;
        }
        JsonNode value = params.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return StrUtil.trimToNull(value.asText());
    }
}
