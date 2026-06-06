package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.ConvertibleBondDailyValuationPO;
import com.scrapider.finance.mapper.ConvertibleBondDailyValuationMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConvertibleBondDailyValuationManage
        extends ServiceImpl<ConvertibleBondDailyValuationMapper, ConvertibleBondDailyValuationPO> {

    public ConvertibleBondDailyValuationPO latestByBondCode(String bondCode) {
        return this.getOne(new LambdaQueryWrapper<ConvertibleBondDailyValuationPO>()
                .eq(StrUtil.isNotBlank(bondCode), ConvertibleBondDailyValuationPO::getBondCode, bondCode)
                .orderByDesc(ConvertibleBondDailyValuationPO::getTradeDate)
                .last("LIMIT 1"));
    }

    public List<ConvertibleBondDailyValuationPO> listByBondCode(String bondCode, Integer limit) {
        LambdaQueryWrapper<ConvertibleBondDailyValuationPO> wrapper =
                new LambdaQueryWrapper<ConvertibleBondDailyValuationPO>()
                        .eq(StrUtil.isNotBlank(bondCode), ConvertibleBondDailyValuationPO::getBondCode, bondCode)
                        .orderByDesc(ConvertibleBondDailyValuationPO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public void saveValuations(List<ConvertibleBondDailyValuationPO> valuations) {
        if (CollUtil.isEmpty(valuations)) {
            return;
        }
        valuations.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(valuations);
    }

    private void fillExistingId(ConvertibleBondDailyValuationPO valuation) {
        ConvertibleBondDailyValuationPO existing = this.getOne(new LambdaQueryWrapper<ConvertibleBondDailyValuationPO>()
                .eq(ConvertibleBondDailyValuationPO::getBondCode, valuation.getBondCode())
                .eq(ConvertibleBondDailyValuationPO::getTradeDate, valuation.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            valuation.setId(existing.getId());
        }
    }
}
