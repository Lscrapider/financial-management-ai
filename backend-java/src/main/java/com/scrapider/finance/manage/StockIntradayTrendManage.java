package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.mapper.StockIntradayTrendMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockIntradayTrendManage extends ServiceImpl<StockIntradayTrendMapper, StockIntradayTrendPO> {

    public void saveTrends(List<StockIntradayTrendPO> trends) {
        if (CollUtil.isEmpty(trends)) {
            return;
        }
        this.saveBatch(trends);
    }

    public String getLatestBatchNo(String stockCode) {
        StockIntradayTrendPO latest = this.getOne(new LambdaQueryWrapper<StockIntradayTrendPO>()
                .select(StockIntradayTrendPO::getSyncBatchNo)
                .eq(StockIntradayTrendPO::getStockCode, stockCode)
                .orderByDesc(StockIntradayTrendPO::getSyncedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getSyncBatchNo();
    }

    public List<StockIntradayTrendPO> listByBatchNo(String stockCode, String syncBatchNo) {
        return this.list(new LambdaQueryWrapper<StockIntradayTrendPO>()
                .eq(StockIntradayTrendPO::getStockCode, stockCode)
                .eq(StockIntradayTrendPO::getSyncBatchNo, syncBatchNo)
                .orderByAsc(StockIntradayTrendPO::getTrendTime));
    }
}
