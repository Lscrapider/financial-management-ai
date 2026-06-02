package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockDailyKlinePO;
import com.scrapider.finance.mapper.StockDailyKlineMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockDailyKlineManage extends ServiceImpl<StockDailyKlineMapper, StockDailyKlinePO> {

    public void saveDailyKlines(List<StockDailyKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    public List<StockDailyKlinePO> listDailyKlines(
            String stockCode,
            String secid,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<StockDailyKlinePO> wrapper = new LambdaQueryWrapper<StockDailyKlinePO>()
                .eq(StrUtil.isNotBlank(stockCode), StockDailyKlinePO::getStockCode, stockCode)
                .eq(StrUtil.isNotBlank(secid), StockDailyKlinePO::getSecid, secid)
                .ge(startDate != null, StockDailyKlinePO::getTradeDate, startDate)
                .le(endDate != null, StockDailyKlinePO::getTradeDate, endDate)
                .orderByDesc(StockDailyKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    private void fillExistingId(StockDailyKlinePO kline) {
        StockDailyKlinePO existing = this.getOne(new LambdaQueryWrapper<StockDailyKlinePO>()
                .eq(StockDailyKlinePO::getSecid, kline.getSecid())
                .eq(StockDailyKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
