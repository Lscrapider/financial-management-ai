package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.BondConfigPO;
import com.scrapider.finance.mapper.BondConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondConfigManage extends ServiceImpl<BondConfigMapper, BondConfigPO> {

    public BondConfigPO getEnabledByBondCode(String bondCode) {
        if (StrUtil.isBlank(bondCode)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<BondConfigPO>()
                .eq(BondConfigPO::getEnabled, true)
                .eq(BondConfigPO::getBondCode, bondCode.trim())
                .last("LIMIT 1"));
    }

    public List<BondConfigPO> listEnabledBonds() {
        return this.list(new LambdaQueryWrapper<BondConfigPO>()
                .eq(BondConfigPO::getEnabled, true)
                .orderByAsc(BondConfigPO::getExchangeCode)
                .orderByAsc(BondConfigPO::getBondCode));
    }

    public List<BondConfigPO> searchEnabledBonds(String keyword, int limit) {
        return this.list(new LambdaQueryWrapper<BondConfigPO>()
                .eq(BondConfigPO::getEnabled, true)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(BondConfigPO::getBondCode, keyword)
                        .or()
                        .like(BondConfigPO::getBondName, keyword)
                        .or()
                        .like(BondConfigPO::getSecid, keyword))
                .orderByAsc(BondConfigPO::getExchangeCode)
                .orderByAsc(BondConfigPO::getBondCode)
                .last("LIMIT " + limit));
    }
}
