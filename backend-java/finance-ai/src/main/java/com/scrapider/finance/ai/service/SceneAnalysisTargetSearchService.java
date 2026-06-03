package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.SceneAnalysisTargetOptionVO;
import java.util.List;

public interface SceneAnalysisTargetSearchService {

    List<SceneAnalysisTargetOptionVO> search(String targetType, String keyword, Integer limit);
}
