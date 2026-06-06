package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.TargetDeleteParam;
import com.scrapider.finance.domain.vo.TargetDeleteResultVO;

public interface SystemConfigTargetDeleteService {

    TargetDeleteResultVO deleteTarget(TargetDeleteParam param);
}
