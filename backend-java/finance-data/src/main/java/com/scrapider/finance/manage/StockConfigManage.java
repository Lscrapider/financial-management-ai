package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
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

    public List<StockConfigPO> searchEnabledStocks(String keyword, int limit) {
        return this.list(new LambdaQueryWrapper<StockConfigPO>()
                .eq(StockConfigPO::getEnabled, true)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(StockConfigPO::getStockCode, keyword)
                        .or()
                        .like(StockConfigPO::getStockName, keyword)
                        .or()
                        .like(StockConfigPO::getSecid, keyword))
                .orderByAsc(StockConfigPO::getStockCode)
                .last("LIMIT " + limit));
    }
}
