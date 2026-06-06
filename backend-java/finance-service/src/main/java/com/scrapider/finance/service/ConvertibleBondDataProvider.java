package com.scrapider.finance.service;

import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import java.util.List;

public interface ConvertibleBondDataProvider {

    ConvertibleBondBasicPO getBasic(BondConfigPO bond);

    List<ConvertibleBondDailyValuationPO> getDailyValuations(BondConfigPO bond, Integer limit);

    List<ConvertibleBondSharePO> getShareChanges(BondConfigPO bond, Integer limit);
}
