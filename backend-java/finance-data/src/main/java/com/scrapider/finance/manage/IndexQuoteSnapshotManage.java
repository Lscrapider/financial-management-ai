package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.IndexQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.po.IndexQuoteSnapshotPO;
import com.scrapider.finance.mapper.IndexQuoteSnapshotMapper;
import java.util.List;
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
}
