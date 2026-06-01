package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVectorPO> {

    @Select("SELECT COUNT(DISTINCT task_no) FROM knowledge_vector")
    Long countDistinctTaskNo();

    @Select("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM knowledge_vector")
    Long sumTextLength();

    @Update("UPDATE knowledge_vector SET metadata = #{metadata}::jsonb WHERE id = #{id}")
    int updateMetadata(@Param("id") Long id, @Param("metadata") String metadata);
}
