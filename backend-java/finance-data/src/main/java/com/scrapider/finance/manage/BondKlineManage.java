package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.BondKlinePO;
import com.scrapider.finance.mapper.BondKlineMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondKlineManage extends ServiceImpl<BondKlineMapper, BondKlinePO> {

    public void saveKlines(List<BondKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    public List<BondKlinePO> listKlines(
            String bondCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<BondKlinePO> wrapper = new LambdaQueryWrapper<BondKlinePO>()
                .eq(StrUtil.isNotBlank(bondCode), BondKlinePO::getBondCode, bondCode)
                .eq(StrUtil.isNotBlank(secid), BondKlinePO::getSecid, secid)
                .eq(BondKlinePO::getPeriodType, periodType.getCode())
                .ge(startDate != null, BondKlinePO::getTradeDate, startDate)
                .le(endDate != null, BondKlinePO::getTradeDate, endDate)
                .orderByDesc(BondKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public boolean hasSyncedSince(String secid, KlinePeriodTypeEnum periodType, LocalDateTime since) {
        if (StrUtil.isBlank(secid) || periodType == null || since == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<BondKlinePO>()
                .eq(BondKlinePO::getSecid, secid)
                .eq(BondKlinePO::getPeriodType, periodType.getCode())
                .ge(BondKlinePO::getSyncedAt, since)) > 0;
    }

    private void fillExistingId(BondKlinePO kline) {
        BondKlinePO existing = this.getOne(new LambdaQueryWrapper<BondKlinePO>()
                .eq(BondKlinePO::getSecid, kline.getSecid())
                .eq(BondKlinePO::getPeriodType, kline.getPeriodType())
                .eq(BondKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
