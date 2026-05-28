package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.mapper.BondConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondConfigManage extends ServiceImpl<BondConfigMapper, BondConfigPO> {

    public List<BondConfigPO> listEnabledBonds() {
        return this.list(new LambdaQueryWrapper<BondConfigPO>()
                .eq(BondConfigPO::getEnabled, true)
                .orderByAsc(BondConfigPO::getExchangeCode)
                .orderByAsc(BondConfigPO::getBondCode));
    }
}
