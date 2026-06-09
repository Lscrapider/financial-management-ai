package com.scrapider.finance.service.provider;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import java.util.List;

public interface ConvertibleBondSceneDataProvider {

    ConvertibleBondBasicPO getBasic(BondConfigPO bond);

    List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit);
}
