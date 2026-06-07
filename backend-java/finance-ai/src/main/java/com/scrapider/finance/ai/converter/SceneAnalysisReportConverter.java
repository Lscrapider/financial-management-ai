package com.scrapider.finance.ai.converter;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportVO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportHistoryDTO;
import com.scrapider.finance.domain.dto.SceneAnalysisReportTargetDTO;
import com.scrapider.finance.domain.enums.SceneAnalysisTaskStatusEnum;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.domain.po.SceneAnalysisTaskPO;
import java.time.LocalDateTime;
import java.util.List;

public final class SceneAnalysisReportConverter {

    private static final int PREVIEW_LENGTH = 120;

    private SceneAnalysisReportConverter() {
    }

    public static SceneAnalysisReportTargetPageVO targetPage(
            List<SceneAnalysisReportTargetVO> records,
            Long total,
            int pageNum,
            int pageSize) {
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + pageSize - 1L) / pageSize;
        return new SceneAnalysisReportTargetPageVO(records, safeTotal, (long) pageNum, (long) pageSize, pages);
    }

    public static SceneAnalysisReportDetailVO detail(SceneAnalysisReportPO report) {
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
                format(report.getGeneratedAt()),
                format(report.getCreatedAt()));
    }

    public static SceneAnalysisReportTargetVO target(SceneAnalysisReportTargetDTO dto) {
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
                preview(dto.getLatestReportText()),
                format(dto.getLatestGeneratedAt()),
                format(dto.getLatestCreatedAt()),
                dto.getReportCount());
    }

    public static SceneAnalysisReportHistoryVO history(SceneAnalysisReportHistoryDTO dto) {
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
                format(dto.getGeneratedAt()),
                format(dto.getCreatedAt()));
    }

    public static SceneAnalysisReportVO notGenerated(SceneAnalysisTaskPO task) {
        return new SceneAnalysisReportVO(
                task.getTaskNo(),
                null,
                task.getStatus(),
                task.getErrorMessage(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static SceneAnalysisReportVO latest(SceneAnalysisTaskPO task, SceneAnalysisReportPO report) {
        boolean finished = SceneAnalysisTaskStatusEnum.SUCCESS.getCode().equals(report.getStatus());
        return new SceneAnalysisReportVO(
                task.getTaskNo(),
                report.getId(),
                report.getStatus(),
                report.getErrorMessage(),
                report.getGenerationType(),
                report.getVersionNo(),
                finished ? report.getReportContent() : null,
                finished ? report.getReportText() : null,
                finished ? report.getModel() : null,
                finished ? format(report.getGeneratedAt()) : null);
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= PREVIEW_LENGTH) {
            return compact;
        }
        return compact.substring(0, PREVIEW_LENGTH) + "...";
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
