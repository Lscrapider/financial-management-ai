package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockConfigPO;
import com.scrapider.finance.mapper.StockConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockConfigManage extends ServiceImpl<StockConfigMapper, StockConfigPO> {

    public List<StockConfigPO> listEnabledStocks() {
        return this.list(new LambdaQueryWrapper<StockConfigPO>()
                .eq(StockConfigPO::getEnabled, true)
                .orderByAsc(StockConfigPO::getStockCode));
    }

    public StockConfigPO getEnabledByStockCode(String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockConfigPO>()
                .eq(StockConfigPO::getEnabled, true)
                .eq(StockConfigPO::getStockCode, stockCode)
                .last("LIMIT 1"));
    }
}
