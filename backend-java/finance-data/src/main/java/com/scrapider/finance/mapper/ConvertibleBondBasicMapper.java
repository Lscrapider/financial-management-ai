package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.ConvertibleBondBasicPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConvertibleBondBasicMapper extends BaseMapper<ConvertibleBondBasicPO> {
}
