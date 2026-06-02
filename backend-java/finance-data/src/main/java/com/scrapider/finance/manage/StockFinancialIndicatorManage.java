package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockFinancialIndicatorPO;
import com.scrapider.finance.mapper.StockFinancialIndicatorMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockFinancialIndicatorManage extends ServiceImpl<StockFinancialIndicatorMapper, StockFinancialIndicatorPO> {

    public StockFinancialIndicatorPO latestByStockCode(String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockFinancialIndicatorPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockFinancialIndicatorPO::getStockCode, stockCode)
                .orderByDesc(StockFinancialIndicatorPO::getReportDate)
                .last("LIMIT 1"));
    }

    public List<StockFinancialIndicatorPO> listByStockCode(String stockCode, Integer limit) {
        LambdaQueryWrapper<StockFinancialIndicatorPO> wrapper = new LambdaQueryWrapper<StockFinancialIndicatorPO>()
                .eq(StrUtil.isNotBlank(stockCode), StockFinancialIndicatorPO::getStockCode, stockCode)
                .orderByDesc(StockFinancialIndicatorPO::getReportDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public void saveFinancialIndicators(List<StockFinancialIndicatorPO> indicators) {
        if (CollUtil.isEmpty(indicators)) {
            return;
        }
        indicators.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(indicators);
    }

    private void fillExistingId(StockFinancialIndicatorPO indicator) {
        StockFinancialIndicatorPO existing = this.getOne(new LambdaQueryWrapper<StockFinancialIndicatorPO>()
                .eq(StockFinancialIndicatorPO::getSecucode, indicator.getSecucode())
                .eq(StockFinancialIndicatorPO::getReportDate, indicator.getReportDate())
                .eq(StockFinancialIndicatorPO::getReportType, indicator.getReportType())
                .last("LIMIT 1"));
        if (existing != null) {
            indicator.setId(existing.getId());
        }
    }
}
