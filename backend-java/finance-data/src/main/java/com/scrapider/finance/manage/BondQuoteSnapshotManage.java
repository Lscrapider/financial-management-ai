package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.BondQuoteSortFieldEnum;
import com.scrapider.finance.domain.enums.SortOrderEnum;
import com.scrapider.finance.domain.po.BondQuoteSnapshotPO;
import com.scrapider.finance.mapper.BondQuoteSnapshotMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BondQuoteSnapshotManage extends ServiceImpl<BondQuoteSnapshotMapper, BondQuoteSnapshotPO> {

    public void saveLatest(BondQuoteSnapshotPO snapshot) {
        BondQuoteSnapshotPO existing = this.getOne(new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                .eq(BondQuoteSnapshotPO::getSecid, snapshot.getSecid())
                .last("LIMIT 1"));
        if (existing != null) {
            snapshot.setId(existing.getId());
        }
        this.saveOrUpdate(snapshot);
    }

    public List<BondQuoteSnapshotPO> listSnapshots(
            String marketCode,
            Integer limit,
            BondQuoteSortFieldEnum sortField,
            SortOrderEnum sortOrder) {
        LambdaQueryWrapper<BondQuoteSnapshotPO> wrapper = new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                .eq(StrUtil.isNotBlank(marketCode), BondQuoteSnapshotPO::getMarketCode, marketCode)
                .orderBy(true, sortOrder.isAsc(), sortField.getColumn())
                .orderByAsc(BondQuoteSnapshotPO::getBondCode);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public List<BondQuoteSnapshotPO> listByBondCodes(Collection<String> bondCodes) {
        if (bondCodes == null || bondCodes.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<BondQuoteSnapshotPO>()
                .in(BondQuoteSnapshotPO::getBondCode, bondCodes));
    }
}
