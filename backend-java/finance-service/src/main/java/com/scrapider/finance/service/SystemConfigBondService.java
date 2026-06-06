package com.scrapider.finance.service;

import com.scrapider.finance.domain.param.BondConfigAddParam;
import com.scrapider.finance.domain.vo.BondConfigAddResultVO;

public interface SystemConfigBondService {

    BondConfigAddResultVO addBond(BondConfigAddParam param);
}
