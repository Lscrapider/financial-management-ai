package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.mapper.StockQuoteSnapshotMapper;
import org.springframework.stereotype.Service;

@Service
public class StockQuoteSnapshotManage extends ServiceImpl<StockQuoteSnapshotMapper, StockQuoteSnapshotPO> {

    public void saveLatest(StockQuoteSnapshotPO snapshot) {
        StockQuoteSnapshotPO existing = this.getOne(new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                .eq(StockQuoteSnapshotPO::getStockCode, snapshot.getStockCode())
                .last("LIMIT 1"));
        if (existing != null) {
            snapshot.setId(existing.getId());
        }
        this.saveOrUpdate(snapshot);
    }
}
