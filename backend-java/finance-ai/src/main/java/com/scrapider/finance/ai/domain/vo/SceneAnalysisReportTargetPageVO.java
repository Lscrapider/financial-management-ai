package com.scrapider.finance.ai.domain.vo;

import java.util.List;

public record SceneAnalysisReportTargetPageVO(
        List<SceneAnalysisReportTargetVO> records,
        Long total,
        Long pageNum,
        Long pageSize,
        Long pages) {
}
