package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.mapper.StockQuoteSnapshotMapper;
import java.util.List;
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

    public List<StockQuoteSnapshotPO> listSnapshots(
            String marketCode,
            Integer limit,
            StockQuoteSortFieldEnum sortField,
            SortOrderEnum sortOrder) {
        LambdaQueryWrapper<StockQuoteSnapshotPO> wrapper = new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                .eq(StrUtil.isNotBlank(marketCode), StockQuoteSnapshotPO::getMarketCode, marketCode)
                .orderBy(true, sortOrder.isAsc(), sortField.getColumn())
                .orderByAsc(StockQuoteSnapshotPO::getStockCode);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }
}
