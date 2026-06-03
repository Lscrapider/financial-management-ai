package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.scrapider.finance.domain.po.IndexConfigPO;
import com.scrapider.finance.mapper.IndexConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndexConfigManage extends ServiceImpl<IndexConfigMapper, IndexConfigPO> {

    public List<IndexConfigPO> listEnabledIndices() {
        return this.list(new LambdaQueryWrapper<IndexConfigPO>()
                .eq(IndexConfigPO::getEnabled, true)
                .orderByAsc(IndexConfigPO::getExchangeCode)
                .orderByAsc(IndexConfigPO::getIndexCode));
    }

    public List<IndexConfigPO> searchEnabledIndices(String keyword, int limit) {
        return this.list(new LambdaQueryWrapper<IndexConfigPO>()
                .eq(IndexConfigPO::getEnabled, true)
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                        .like(IndexConfigPO::getIndexCode, keyword)
                        .or()
                        .like(IndexConfigPO::getIndexName, keyword)
                        .or()
                        .like(IndexConfigPO::getSecid, keyword))
                .orderByAsc(IndexConfigPO::getExchangeCode)
                .orderByAsc(IndexConfigPO::getIndexCode)
                .last("LIMIT " + limit));
    }
}
