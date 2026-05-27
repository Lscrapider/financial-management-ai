package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.mapper.StockAlertConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockAlertConfigManage extends ServiceImpl<StockAlertConfigMapper, StockAlertConfigPO> {

    public List<StockAlertConfigPO> listByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getUserId, userId)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }

    public List<StockAlertConfigPO> listAll() {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .orderByAsc(StockAlertConfigPO::getUserId)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }

    public StockAlertConfigPO getByUserIdAndStockCode(Long userId, String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getUserId, userId)
                .eq(StockAlertConfigPO::getStockCode, stockCode)
                .last("LIMIT 1"));
    }

    public StockAlertConfigPO getByIdAndUserId(Long id, Long userId) {
        return this.getOne(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getId, id)
                .eq(StockAlertConfigPO::getUserId, userId)
                .last("LIMIT 1"));
    }

    public List<StockAlertConfigPO> listEnabled() {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getEnabled, true)
                .orderByAsc(StockAlertConfigPO::getUserId)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }
}
