package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.enums.KlinePeriodTypeEnum;
import com.scrapider.finance.domain.po.IndexKlinePO;
import com.scrapider.finance.mapper.IndexKlineMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndexKlineManage extends ServiceImpl<IndexKlineMapper, IndexKlinePO> {

    public void saveKlines(List<IndexKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    public List<IndexKlinePO> listKlines(
            String indexCode,
            String secid,
            KlinePeriodTypeEnum periodType,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<IndexKlinePO> wrapper = new LambdaQueryWrapper<IndexKlinePO>()
                .eq(StrUtil.isNotBlank(indexCode), IndexKlinePO::getIndexCode, indexCode)
                .eq(StrUtil.isNotBlank(secid), IndexKlinePO::getSecid, secid)
                .eq(IndexKlinePO::getPeriodType, periodType.getCode())
                .ge(startDate != null, IndexKlinePO::getTradeDate, startDate)
                .le(endDate != null, IndexKlinePO::getTradeDate, endDate)
                .orderByDesc(IndexKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    public boolean hasSyncedSince(String secid, KlinePeriodTypeEnum periodType, LocalDateTime since) {
        if (StrUtil.isBlank(secid) || periodType == null || since == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<IndexKlinePO>()
                .eq(IndexKlinePO::getSecid, secid)
                .eq(IndexKlinePO::getPeriodType, periodType.getCode())
                .ge(IndexKlinePO::getSyncedAt, since)) > 0;
    }

    private void fillExistingId(IndexKlinePO kline) {
        IndexKlinePO existing = this.getOne(new LambdaQueryWrapper<IndexKlinePO>()
                .eq(IndexKlinePO::getSecid, kline.getSecid())
                .eq(IndexKlinePO::getPeriodType, kline.getPeriodType())
                .eq(IndexKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
