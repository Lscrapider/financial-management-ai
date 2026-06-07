package com.scrapider.finance.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.ai.converter.SceneAnalysisReportConverter;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportDetailVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportHistoryVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetPageVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTargetVO;
import com.scrapider.finance.ai.security.CurrentUserContext;
import com.scrapider.finance.ai.service.SceneAnalysisReportQueryService;
import com.scrapider.finance.domain.po.SceneAnalysisReportPO;
import com.scrapider.finance.manage.SceneAnalysisReportManage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SceneAnalysisReportQueryServiceImpl implements SceneAnalysisReportQueryService {

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
        Long ownerUserId = CurrentUserContext.ownerUserIdForQuery();
        Long total = this.sceneAnalysisReportManage
                .countTargets(normalizedTargetName, normalizedTargetCode, normalizedTargetType, ownerUserId);
        List<SceneAnalysisReportTargetVO> records = this.sceneAnalysisReportManage
                .listTargets(
                        normalizedTargetName,
                        normalizedTargetCode,
                        normalizedTargetType,
                        ownerUserId,
                        ps,
                        (long) (pn - 1) * ps)
                .stream()
                .map(SceneAnalysisReportConverter::target)
                .toList();
        return SceneAnalysisReportConverter.targetPage(records, total, pn, ps);
    }

    @Override
    public List<SceneAnalysisReportHistoryVO> listHistory(String targetType, String targetCode) {
        if (StrUtil.isBlank(targetType)) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (StrUtil.isBlank(targetCode)) {
            throw new IllegalArgumentException("targetCode is required");
        }
        return this.sceneAnalysisReportManage
                .listHistory(targetType, targetCode, CurrentUserContext.ownerUserIdForQuery())
                .stream()
                .map(SceneAnalysisReportConverter::history)
                .toList();
    }

    @Override
    public SceneAnalysisReportDetailVO detail(Long reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("reportId is required");
        }
        SceneAnalysisReportPO report = this.sceneAnalysisReportManage
                .findByIdForOwner(reportId, CurrentUserContext.ownerUserIdForQuery());
        if (report == null) {
            throw new IllegalArgumentException("scene analysis report not found: " + reportId);
        }
        return SceneAnalysisReportConverter.detail(report);
    }

    private String normalizeText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        return text.trim();
    }

}
