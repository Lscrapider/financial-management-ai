package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import com.scrapider.finance.mapper.ConvertibleBondBasicMapper;
import org.springframework.stereotype.Service;

@Service
public class ConvertibleBondBasicManage extends ServiceImpl<ConvertibleBondBasicMapper, ConvertibleBondBasicPO> {

    public ConvertibleBondBasicPO latestByBondCode(String bondCode) {
        return this.getOne(new LambdaQueryWrapper<ConvertibleBondBasicPO>()
                .eq(StrUtil.isNotBlank(bondCode), ConvertibleBondBasicPO::getBondCode, bondCode)
                .orderByDesc(ConvertibleBondBasicPO::getSyncedAt)
                .last("LIMIT 1"));
    }

    public void saveBasic(ConvertibleBondBasicPO basic) {
        if (basic == null) {
            return;
        }
        ConvertibleBondBasicPO existing = this.latestByBondCode(basic.getBondCode());
        if (existing != null) {
            basic.setId(existing.getId());
        }
        this.saveOrUpdate(basic);
    }
}
