package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockAlertConfigPO;
import com.scrapider.finance.mapper.StockAlertConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockAlertConfigManage extends ServiceImpl<StockAlertConfigMapper, StockAlertConfigPO> {

    public List<StockAlertConfigPO> listByUserId(Long userId, String targetType) {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getUserId, userId)
                .eq(StrUtil.isNotBlank(targetType), StockAlertConfigPO::getTargetType, targetType)
                .orderByAsc(StockAlertConfigPO::getTargetType)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }

    public List<StockAlertConfigPO> listAll(String targetType) {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StrUtil.isNotBlank(targetType), StockAlertConfigPO::getTargetType, targetType)
                .orderByAsc(StockAlertConfigPO::getUserId)
                .orderByAsc(StockAlertConfigPO::getTargetType)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }

    public StockAlertConfigPO getByUserIdAndTarget(Long userId, String targetType, String stockCode) {
        return this.getOne(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getUserId, userId)
                .eq(StockAlertConfigPO::getTargetType, targetType)
                .eq(StockAlertConfigPO::getStockCode, stockCode)
                .last("LIMIT 1"));
    }

    public List<StockAlertConfigPO> listEnabled() {
        return this.list(new LambdaQueryWrapper<StockAlertConfigPO>()
                .eq(StockAlertConfigPO::getEnabled, true)
                .orderByAsc(StockAlertConfigPO::getUserId)
                .orderByAsc(StockAlertConfigPO::getTargetType)
                .orderByAsc(StockAlertConfigPO::getStockCode));
    }
}
