package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportTargetDTO;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import com.scrapider.finance.mapper.SceneAnalysisReportMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisReportManage extends ServiceImpl<SceneAnalysisReportMapper, SceneAnalysisReportPO> {

    private final ObjectMapper objectMapper;

    public SceneAnalysisReportManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SceneAnalysisReportPO createGeneratingReport(SceneAnalysisTaskPO task, String generationType) {
        Integer nextVersionNo = this.baseMapper.maxVersionNo(task.getId()) + 1;
        SceneAnalysisReportPO report = SceneAnalysisReportPO.createGenerating(task, generationType, nextVersionNo);
        this.baseMapper.insertReport(report);
        return report;
    }

    public SceneAnalysisReportPO latestByTaskNo(String taskNo) {
        return this.baseMapper.latestByTaskNo(taskNo);
    }

    public Long countTargets(String targetName, String targetCode, String targetType, Long ownerUserId) {
        return this.baseMapper.countTargets(targetName, targetCode, targetType, ownerUserId);
    }

    public List<SceneAnalysisReportTargetDTO> listTargets(
            String targetName,
            String targetCode,
            String targetType,
            Long ownerUserId,
            int limit,
            long offset) {
        return this.baseMapper.listTargets(targetName, targetCode, targetType, ownerUserId, limit, offset);
    }

    public List<SceneAnalysisReportHistoryDTO> listHistory(String targetType, String targetCode, Long ownerUserId) {
        return this.baseMapper.listHistory(targetType, targetCode, ownerUserId);
    }

    public SceneAnalysisReportPO findByIdForOwner(Long reportId, Long ownerUserId) {
        return this.baseMapper.findByIdForOwner(reportId, ownerUserId);
    }

    public void markSuccess(Long reportId, JsonNode reportContent, String reportText, String model) {
        LocalDateTime now = LocalDateTime.now();
        this.baseMapper.markSuccess(
                reportId,
                SceneAnalysisTaskStatusEnum.SUCCESS.getCode(),
                this.toJson(reportContent),
                reportText,
                model,
                now,
                now);
    }

    public void markFailed(Long reportId, String errorMessage) {
        this.baseMapper.markFailed(
                reportId,
                SceneAnalysisTaskStatusEnum.FAILED.getCode(),
                errorMessage,
                LocalDateTime.now());
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "{}";
        }
        try {
            return this.objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize scene analysis report json payload", ex);
        }
    }
}
