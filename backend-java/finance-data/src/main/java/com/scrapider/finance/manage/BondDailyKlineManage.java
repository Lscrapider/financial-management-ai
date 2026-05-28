package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.BondDailyKlinePO;
import com.scrapider.finance.mapper.BondDailyKlineMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondDailyKlineManage extends ServiceImpl<BondDailyKlineMapper, BondDailyKlinePO> {

    public void saveDailyKlines(List<BondDailyKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    public List<BondDailyKlinePO> listDailyKlines(
            String bondCode,
            String secid,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<BondDailyKlinePO> wrapper = new LambdaQueryWrapper<BondDailyKlinePO>()
                .eq(StrUtil.isNotBlank(bondCode), BondDailyKlinePO::getBondCode, bondCode)
                .eq(StrUtil.isNotBlank(secid), BondDailyKlinePO::getSecid, secid)
                .ge(startDate != null, BondDailyKlinePO::getTradeDate, startDate)
                .le(endDate != null, BondDailyKlinePO::getTradeDate, endDate)
                .orderByDesc(BondDailyKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    private void fillExistingId(BondDailyKlinePO kline) {
        BondDailyKlinePO existing = this.getOne(new LambdaQueryWrapper<BondDailyKlinePO>()
                .eq(BondDailyKlinePO::getSecid, kline.getSecid())
                .eq(BondDailyKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
