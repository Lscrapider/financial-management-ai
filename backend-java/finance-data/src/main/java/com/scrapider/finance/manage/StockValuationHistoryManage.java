package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockValuationHistoryPO;
import com.scrapider.finance.mapper.StockValuationHistoryMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockValuationHistoryManage extends ServiceImpl<StockValuationHistoryMapper, StockValuationHistoryPO> {

    public StockValuationHistoryPO latestByStockCode(String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockValuationHistoryPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockValuationHistoryPO::getStockCode, stockCode)
                .orderByDesc(StockValuationHistoryPO::getTradeDate)
                .last("LIMIT 1"));
    }

    public List<StockValuationHistoryPO> listByStockCode(String stockCode, Integer limit) {
        LambdaQueryWrapper<StockValuationHistoryPO> wrapper = new LambdaQueryWrapper<StockValuationHistoryPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockValuationHistoryPO::getStockCode, stockCode)
                .orderByDesc(StockValuationHistoryPO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public void saveValuationHistory(List<StockValuationHistoryPO> valuations) {
        if (CollUtil.isEmpty(valuations)) {
            return;
        }
        valuations.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(valuations);
    }

    private void fillExistingId(StockValuationHistoryPO valuation) {
        StockValuationHistoryPO existing = this.getOne(new LambdaQueryWrapper<StockValuationHistoryPO>()
                .eq(StockValuationHistoryPO::getSecid, valuation.getSecid())
                .eq(StockValuationHistoryPO::getTradeDate, valuation.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            valuation.setId(existing.getId());
        }
    }
}
