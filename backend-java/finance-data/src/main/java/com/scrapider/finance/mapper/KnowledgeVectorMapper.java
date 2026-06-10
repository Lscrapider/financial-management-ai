package com.scrapider.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
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

    @Select("SELECT scenes.key AS category, tag.value AS tag, COUNT(*) AS cnt " +
            "FROM knowledge_vector, " +
            "jsonb_each(metadata -> 'scenes') AS scenes, " +
            "jsonb_array_elements_text(scenes.value) AS tag " +
            "GROUP BY scenes.key, tag.value " +
            "ORDER BY scenes.key, cnt DESC")
    List<Map<String, Object>> tagDistribution();

    @Results(id = "knowledgeVectorSearchResult", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_no", property = "taskNo"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "text", property = "text"),
            @Result(column = "metadata", property = "metadata", typeHandler = JacksonTypeHandler.class),
            @Result(column = "semantic_score", property = "semanticScore")
    })
    @Select("""
            SELECT id,
                   task_no,
                   chunk_index,
                   text,
                   metadata,
                   GREATEST(0, 1 - (embedding <=> CAST(#{queryEmbedding} AS vector))) AS semantic_score
            FROM knowledge_vector
            WHERE embedding IS NOT NULL
              AND COALESCE((metadata ->> 'deleted')::boolean, false) = false
              AND metadata -> 'scenes' -> #{scene} IS NOT NULL
            ORDER BY embedding <=> CAST(#{queryEmbedding} AS vector)
            LIMIT #{limit}
            """)
    List<KnowledgeVectorSearchDTO> searchBySemantic(
            @Param("scene") String scene,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);

    @ResultMap("knowledgeVectorSearchResult")
    @Select("""
            <script>
            SELECT id,
                   task_no,
                   chunk_index,
                   text,
                   metadata,
                   GREATEST(0, 1 - (embedding &lt;=&gt; CAST(#{queryEmbedding} AS vector))) AS semantic_score
            FROM knowledge_vector
            WHERE embedding IS NOT NULL
              AND COALESCE((metadata ->> 'deleted')::boolean, false) = false
            <if test='scenes != null and scenes.size() > 0'>
              AND (
                <foreach collection='scenes' item='scene' separator=' OR '>
                  metadata -> 'scenes' -> #{scene} IS NOT NULL
                </foreach>
              )
            </if>
            ORDER BY embedding &lt;=&gt; CAST(#{queryEmbedding} AS vector)
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeVectorSearchDTO> searchBySemanticScenes(
            @Param("scenes") List<String> scenes,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit);
}
