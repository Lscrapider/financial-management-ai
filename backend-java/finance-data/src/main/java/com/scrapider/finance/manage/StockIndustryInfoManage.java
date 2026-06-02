package com.scrapider.finance.manage;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.StockIndustryInfoPO;
import com.scrapider.finance.mapper.StockIndustryInfoMapper;
import org.springframework.stereotype.Service;

@Service
public class StockIndustryInfoManage extends ServiceImpl<StockIndustryInfoMapper, StockIndustryInfoPO> {

    public StockIndustryInfoPO getBySecid(String secid) {
        return this.getOne(new LambdaQueryWrapper<StockIndustryInfoPO>()
                .eq(StrUtil.isNotBlank(secid), StockIndustryInfoPO::getSecid, secid)
                .last("LIMIT 1"));
    }

    public void saveIndustryInfo(StockIndustryInfoPO industryInfo) {
        if (industryInfo == null) {
            return;
        }
        StockIndustryInfoPO existing = this.getBySecid(industryInfo.getSecid());
        if (existing != null) {
            industryInfo.setId(existing.getId());
        }
        this.saveOrUpdate(industryInfo);
    }
}
