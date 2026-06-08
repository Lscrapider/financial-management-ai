package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.KlineAdjustTypeEnum;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.StockKlinePO;
import com.scrapider.finance.mapper.StockKlineMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockKlineManage extends ServiceImpl<StockKlineMapper, StockKlinePO> {

    @Transactional(rollbackFor = Exception.class)
    public void saveKlines(List<StockKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveDailyKlines(List<StockKlinePO> klines) {
        this.saveKlines(klines);
    }

    public List<StockKlinePO> listKlines(
            String stockCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<StockKlinePO> wrapper = new LambdaQueryWrapper<StockKlinePO>()
                .eq(StrUtil.isNotBlank(stockCode), StockKlinePO::getStockCode, stockCode)
                .eq(StrUtil.isNotBlank(secid), StockKlinePO::getSecid, secid)
                .eq(periodType != null, StockKlinePO::getPeriodType, periodType == null ? null : periodType.getCode())
                .eq(adjustType != null, StockKlinePO::getAdjustType, adjustType == null ? null : adjustType.getCode())
                .ge(startDate != null, StockKlinePO::getTradeDate, startDate)
                .le(endDate != null, StockKlinePO::getTradeDate, endDate)
                .orderByDesc(StockKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public List<StockKlinePO> listDailyKlines(
            String stockCode,
            String secid,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        return this.listKlines(
                stockCode,
                secid,
                KlinePeriodTypeEnum.DAILY,
                KlineAdjustTypeEnum.HFQ,
                startDate,
                endDate,
                limit);
    }

    public boolean hasSyncedSince(
            String secid,
            KlinePeriodTypeEnum periodType,
            KlineAdjustTypeEnum adjustType,
            LocalDateTime since) {
        if (StrUtil.isBlank(secid) || periodType == null || adjustType == null || since == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<StockKlinePO>()
                .eq(StockKlinePO::getSecid, secid)
                .eq(StockKlinePO::getPeriodType, periodType.getCode())
                .eq(StockKlinePO::getAdjustType, adjustType.getCode())
                .ge(StockKlinePO::getSyncedAt, since)) > 0;
    }

    private void fillExistingId(StockKlinePO kline) {
        StockKlinePO existing = this.getOne(new LambdaQueryWrapper<StockKlinePO>()
                .eq(StockKlinePO::getSecid, kline.getSecid())
                .eq(StockKlinePO::getPeriodType, kline.getPeriodType())
                .eq(StockKlinePO::getAdjustType, kline.getAdjustType())
                .eq(StockKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
