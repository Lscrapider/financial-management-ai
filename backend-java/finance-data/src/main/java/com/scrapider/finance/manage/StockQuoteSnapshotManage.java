package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.enums.StockQuoteSortFieldEnum;
import com.scrapider.finance.domain.po.StockQuoteSnapshotPO;
import com.scrapider.finance.mapper.StockQuoteSnapshotMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public void saveQuotesBatch(List<StockQuoteSnapshotPO> snapshots) {
        if (CollUtil.isEmpty(snapshots)) {
            return;
        }
        Set<String> stockCodes = snapshots.stream()
                .map(StockQuoteSnapshotPO::getStockCode)
                .collect(Collectors.toSet());
        Map<String, StockQuoteSnapshotPO> existingMap = this.list(new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                        .in(StockQuoteSnapshotPO::getStockCode, stockCodes))
                .stream()
                .collect(Collectors.toMap(StockQuoteSnapshotPO::getStockCode, Function.identity(), (a, b) -> a));
        for (StockQuoteSnapshotPO snapshot : snapshots) {
            StockQuoteSnapshotPO existing = existingMap.get(snapshot.getStockCode());
            if (existing != null) {
                snapshot.setId(existing.getId());
            }
        }
        this.saveOrUpdateBatch(snapshots);
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

    public List<StockQuoteSnapshotPO> listByStockCodes(Collection<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<StockQuoteSnapshotPO>()
                .in(StockQuoteSnapshotPO::getStockCode, stockCodes));
    }
}
