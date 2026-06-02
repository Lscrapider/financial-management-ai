package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockDividendHistoryPO;
import com.scrapider.finance.mapper.StockDividendHistoryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockDividendHistoryManage extends ServiceImpl<StockDividendHistoryMapper, StockDividendHistoryPO> {

    public StockDividendHistoryPO latestByStockCode(String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockDividendHistoryPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockDividendHistoryPO::getStockCode, stockCode)
                .orderByDesc(StockDividendHistoryPO::getExDividendDate)
                .last("LIMIT 1"));
    }

    public List<StockDividendHistoryPO> listByStockCode(String stockCode, Integer limit) {
        LambdaQueryWrapper<StockDividendHistoryPO> wrapper = new LambdaQueryWrapper<StockDividendHistoryPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockDividendHistoryPO::getStockCode, stockCode)
                .orderByDesc(StockDividendHistoryPO::getExDividendDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public void saveDividendHistory(List<StockDividendHistoryPO> dividends) {
        if (CollUtil.isEmpty(dividends)) {
            return;
        }
        dividends.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(dividends);
    }

    private void fillExistingId(StockDividendHistoryPO dividend) {
        StockDividendHistoryPO existing = this.getOne(new LambdaQueryWrapper<StockDividendHistoryPO>()
                .eq(StockDividendHistoryPO::getSecucode, dividend.getSecucode())
                .eq(StockDividendHistoryPO::getReportDate, dividend.getReportDate())
                .eq(StockDividendHistoryPO::getExDividendDate, dividend.getExDividendDate())
                .last("LIMIT 1"));
        if (existing != null) {
            dividend.setId(existing.getId());
        }
    }
}
