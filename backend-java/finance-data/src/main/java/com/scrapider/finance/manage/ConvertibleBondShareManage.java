package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.ConvertibleBondSharePO;
import com.scrapider.finance.mapper.ConvertibleBondShareMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConvertibleBondShareManage extends ServiceImpl<ConvertibleBondShareMapper, ConvertibleBondSharePO> {

    public ConvertibleBondSharePO latestByBondCode(String bondCode) {
        return this.getOne(new LambdaQueryWrapper<ConvertibleBondSharePO>()
                .eq(StrUtil.isNotBlank(bondCode), ConvertibleBondSharePO::getBondCode, bondCode)
                .orderByDesc(ConvertibleBondSharePO::getEndDate)
                .last("LIMIT 1"));
    }

    public void saveShares(List<ConvertibleBondSharePO> shares) {
        if (CollUtil.isEmpty(shares)) {
            return;
        }
        shares.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(shares);
    }

    private void fillExistingId(ConvertibleBondSharePO share) {
        ConvertibleBondSharePO existing = this.getOne(new LambdaQueryWrapper<ConvertibleBondSharePO>()
                .eq(ConvertibleBondSharePO::getBondCode, share.getBondCode())
                .eq(ConvertibleBondSharePO::getEndDate, share.getEndDate())
                .last("LIMIT 1"));
        if (existing != null) {
            share.setId(existing.getId());
        }
    }
}
