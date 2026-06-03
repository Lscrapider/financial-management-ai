package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import com.scrapider.finance.ai.service.SceneAnalysisReportQueryService;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportTargetDTO;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisReportQueryServiceImpl implements SceneAnalysisReportQueryService {

    private static final int PREVIEW_LENGTH = 120;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SceneAnalysisReportManage sceneAnalysisReportManage;

    public SceneAnalysisReportQueryServiceImpl(SceneAnalysisReportManage sceneAnalysisReportManage) {
        this.sceneAnalysisReportManage = sceneAnalysisReportManage;
    }

    @Override
    public SceneAnalysisReportTargetPageVO pageTargets(
            int pageNum,
            int pageSize,
            String targetName,
            String targetCode,
            String targetType) {
        int pn = Math.max(pageNum, DEFAULT_PAGE_NUM);
        int ps = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedTargetName = this.normalizeText(targetName);
        String normalizedTargetCode = this.normalizeText(targetCode);
        String normalizedTargetType = this.normalizeText(targetType);
        Long total = this.sceneAnalysisReportManage
                .countTargets(normalizedTargetName, normalizedTargetCode, normalizedTargetType);
        List<SceneAnalysisReportTargetVO> records = this.sceneAnalysisReportManage
                .listTargets(normalizedTargetName, normalizedTargetCode, normalizedTargetType, ps, (long) (pn - 1) * ps)
                .stream()
                .map(this::toTargetVO)
                .toList();
        long pages = total == null || total == 0 ? 0 : (total + ps - 1) / ps;
        return new SceneAnalysisReportTargetPageVO(records, total == null ? 0 : total, (long) pn, (long) ps, pages);
    }

    @Override
    public List<SceneAnalysisReportHistoryVO> listHistory(String targetType, String targetCode) {
        if (StrUtil.isBlank(targetType)) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetCode is required");
        }
        return this.sceneAnalysisReportManage.listHistory(targetType, targetCode).stream()
                .map(this::toHistoryVO)
                .toList();
    }

    @Override
    public SceneAnalysisReportDetailVO detail(Long reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId is required");
        }
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage.getById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("scene analysis report not found: " + reportId);
        }
        return new SceneAnalysisReportDetailVO(
                report.getId(),
                report.getTaskId(),
                report.getTaskNo(),
                report.getTargetType(),
                report.getTargetCode(),
                report.getTargetName(),
                report.getReportType(),
                report.getGenerationType(),
                report.getVersionNo(),
                report.getStatus(),
                report.getReportContent(),
                report.getReportText(),
                report.getModel(),
                report.getErrorMessage(),
                this.format(report.getGeneratedAt()),
                this.format(report.getCreatedAt()));
    }

    private SceneAnalysisReportTargetVO toTargetVO(SceneAnalysisReportTargetDTO dto) {
        return new SceneAnalysisReportTargetVO(
                dto.getTargetType(),
                dto.getTargetCode(),
                dto.getTargetName(),
                dto.getLatestReportId(),
                dto.getLatestTaskNo(),
                dto.getLatestStatus(),
                dto.getLatestReportType(),
                dto.getLatestGenerationType(),
                dto.getLatestVersionNo(),
                dto.getLatestModel(),
                this.preview(dto.getLatestReportText()),
                this.format(dto.getLatestGeneratedAt()),
                this.format(dto.getLatestCreatedAt()),
                dto.getReportCount());
    }

    private SceneAnalysisReportHistoryVO toHistoryVO(SceneAnalysisReportHistoryDTO dto) {
        return new SceneAnalysisReportHistoryVO(
                dto.getReportId(),
                dto.getTaskNo(),
                dto.getTargetType(),
                dto.getTargetCode(),
                dto.getTargetName(),
                dto.getReportType(),
                dto.getGenerationType(),
                dto.getVersionNo(),
                dto.getStatus(),
                dto.getModel(),
                dto.getErrorMessage(),
                this.format(dto.getGeneratedAt()),
                this.format(dto.getCreatedAt()));
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= PREVIEW_LENGTH) {
            return compact;
        }
        return compact.substring(0, PREVIEW_LENGTH) + "...";
    }

    private String normalizeText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        return text.trim();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
