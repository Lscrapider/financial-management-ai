package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.SceneAnalysisConfigProfileParam;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigGroupVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisConfigProfileVO;
import com.scrapider.finance.ai.domain.vo.SceneAnalysisReportTypeVO;
import java.util.List;

public interface SceneAnalysisConfigProfileService {

    List<SceneAnalysisConfigGroupVO> parameterSchema();

    List<SceneAnalysisReportTypeVO> reportTypes();

    List<SceneAnalysisConfigProfileVO> listProfiles();

    SceneAnalysisConfigProfileVO create(SceneAnalysisConfigProfileParam param);

    SceneAnalysisConfigProfileVO update(Long id, SceneAnalysisConfigProfileParam param);

    void delete(Long id);
}
