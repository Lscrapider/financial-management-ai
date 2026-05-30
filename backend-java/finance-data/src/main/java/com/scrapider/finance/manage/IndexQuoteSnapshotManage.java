package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.mapper.IndexQuoteSnapshotMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class IndexQuoteSnapshotManage extends ServiceImpl<IndexQuoteSnapshotMapper, IndexQuoteSnapshotPO> {

    public void saveLatest(IndexQuoteSnapshotPO snapshot) {
        IndexQuoteSnapshotPO existing = this.getOne(new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                .eq(IndexQuoteSnapshotPO::getSecid, snapshot.getSecid())
                .last("LIMIT 1"));
        if (existing != null) {
            snapshot.setId(existing.getId());
        }
        this.saveOrUpdate(snapshot);
    }

    public void saveQuotesBatch(List<IndexQuoteSnapshotPO> snapshots) {
        if (CollUtil.isEmpty(snapshots)) {
            return;
        }
        Set<String> secids = snapshots.stream()
                .map(IndexQuoteSnapshotPO::getSecid)
                .collect(Collectors.toSet());
        Map<String, IndexQuoteSnapshotPO> existingMap = this.list(new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                        .in(IndexQuoteSnapshotPO::getSecid, secids))
                .stream()
                .collect(Collectors.toMap(IndexQuoteSnapshotPO::getSecid, Function.identity(), (a, b) -> a));
        for (IndexQuoteSnapshotPO snapshot : snapshots) {
            IndexQuoteSnapshotPO existing = existingMap.get(snapshot.getSecid());
            if (existing != null) {
                snapshot.setId(existing.getId());
            }
        }
        this.saveOrUpdateBatch(snapshots);
    }

    public List<IndexQuoteSnapshotPO> listSnapshots(
            String marketCode,
            Integer limit,
            IndexQuoteSortFieldEnum sortField,
            SortOrderEnum sortOrder) {
        LambdaQueryWrapper<IndexQuoteSnapshotPO> wrapper = new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                .eq(StrUtil.isNotBlank(marketCode), IndexQuoteSnapshotPO::getMarketCode, marketCode)
                .orderBy(true, sortOrder.isAsc(), sortField.getColumn())
                .orderByAsc(IndexQuoteSnapshotPO::getIndexCode);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public List<IndexQuoteSnapshotPO> listByIndexCodes(Collection<String> indexCodes) {
        if (indexCodes == null || indexCodes.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<IndexQuoteSnapshotPO>()
                .in(IndexQuoteSnapshotPO::getIndexCode, indexCodes));
    }
}
