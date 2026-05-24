package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.IndexDailyKlinePO;
import com.scrapider.finance.mapper.IndexDailyKlineMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndexDailyKlineManage extends ServiceImpl<IndexDailyKlineMapper, IndexDailyKlinePO> {

    public void saveDailyKlines(List<IndexDailyKlinePO> klines) {
        if (CollUtil.isEmpty(klines)) {
            return;
        }
        klines.forEach(this::fillExistingId);
        this.saveOrUpdateBatch(klines);
    }

    public List<IndexDailyKlinePO> listDailyKlines(
            String indexCode,
            String secid,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        LambdaQueryWrapper<IndexDailyKlinePO> wrapper = new LambdaQueryWrapper<IndexDailyKlinePO>()
                .eq(StrUtil.isNotBlank(indexCode), IndexDailyKlinePO::getIndexCode, indexCode)
                .eq(StrUtil.isNotBlank(secid), IndexDailyKlinePO::getSecid, secid)
                .ge(startDate != null, IndexDailyKlinePO::getTradeDate, startDate)
                .le(endDate != null, IndexDailyKlinePO::getTradeDate, endDate)
                .orderByDesc(IndexDailyKlinePO::getTradeDate);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return this.list(wrapper);
    }

    private void fillExistingId(IndexDailyKlinePO kline) {
        IndexDailyKlinePO existing = this.getOne(new LambdaQueryWrapper<IndexDailyKlinePO>()
                .eq(IndexDailyKlinePO::getSecid, kline.getSecid())
                .eq(IndexDailyKlinePO::getTradeDate, kline.getTradeDate())
                .last("LIMIT 1"));
        if (existing != null) {
            kline.setId(existing.getId());
        }
    }
}
