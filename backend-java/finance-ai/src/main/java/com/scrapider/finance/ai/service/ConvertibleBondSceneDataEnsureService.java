package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.ConvertibleBondSceneDataDTO;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;

public interface ConvertibleBondSceneDataEnsureService {

    ConvertibleBondSceneDataDTO ensureBondSceneData(
            BondConfigPO bond,
            BondQuoteSnapshotPO quote,
            Integer valuationLimit,
            Integer shareLimit);
}
