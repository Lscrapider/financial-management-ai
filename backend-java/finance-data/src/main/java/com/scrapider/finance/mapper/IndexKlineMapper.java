package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.IndexKlinePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IndexKlineMapper extends BaseMapper<IndexKlinePO> {
}
