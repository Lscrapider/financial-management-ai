package com.scrapider.finance.manage;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockIntradayTrendPO;
import com.scrapider.finance.mapper.StockIntradayTrendMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StockIntradayTrendManage extends ServiceImpl<StockIntradayTrendMapper, StockIntradayTrendPO> {

    public void saveTrends(List<StockIntradayTrendPO> trends) {
        if (CollUtil.isEmpty(trends)) {
            return;
        }
        this.saveBatch(trends);
    }
}
