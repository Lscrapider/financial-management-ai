package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVectorPO> {

    @Select("SELECT COUNT(DISTINCT task_no) FROM knowledge_vector")
    Long countDistinctTaskNo();

    @Select("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM knowledge_vector")
    Long sumTextLength();
}
