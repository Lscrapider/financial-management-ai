package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.scrapider.finance.domain.dto.KnowledgeVectorSearchDTO;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import com.scrapider.finance.mapper.KnowledgeVectorMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeVectorManage extends ServiceImpl<KnowledgeVectorMapper, KnowledgeVectorPO> {

    public Page<KnowledgeVectorPO> pageChunks(int pageNum, int pageSize, Set<String> taskNos,
            String category, List<String> tags) {
        var query = this.lambdaQuery()
                .in(taskNos != null && !taskNos.isEmpty(), KnowledgeVectorPO::getTaskNo, taskNos)
                .orderByAsc(KnowledgeVectorPO::getTaskNo)
                .orderByAsc(KnowledgeVectorPO::getChunkIndex);
        if (tags != null && !tags.isEmpty()) {
            List<String> safeTags = tags.stream().filter(t -> t != null && !t.isBlank()).toList();
            if (!safeTags.isEmpty()) {
                if (category != null && !category.isBlank()) {
                    StringBuilder sql = new StringBuilder("(");
                    Object[] params = new Object[safeTags.size() + 1];
                    params[0] = category;
                    for (int i = 0; i < safeTags.size(); i++) {
                        if (i > 0) sql.append(" OR ");
                        sql.append("metadata -> 'scenes' -> {0} @> jsonb_build_array({")
                                .append(i + 1).append("})");
                        params[i + 1] = safeTags.get(i);
                    }
                    sql.append(")");
                    query.apply(sql.toString(), params);
                } else {
                    StringBuilder sql = new StringBuilder(
                            "EXISTS (SELECT 1 FROM jsonb_each(metadata -> 'scenes') AS sc WHERE ");
                    for (int i = 0; i < safeTags.size(); i++) {
                        if (i > 0) sql.append(" OR ");
                        sql.append("sc.value @> jsonb_build_array({").append(i).append("})");
                    }
                    sql.append(")");
                    query.apply(sql.toString(), safeTags.toArray());
                }
            }
        }
        return query.page(Page.of(pageNum, pageSize));
    }

    public KnowledgeVectorPO findById(Long id) {
        return this.lambdaQuery()
                .eq(KnowledgeVectorPO::getId, id)
                .one();
    }

    public void deleteByTaskNo(String taskNo) {
        this.lambdaUpdate()
                .eq(KnowledgeVectorPO::getTaskNo, taskNo)
                .remove();
    }

    public void updateText(Long id, String newText) {
        this.lambdaUpdate()
                .eq(KnowledgeVectorPO::getId, id)
                .set(KnowledgeVectorPO::getText, newText)
                .update();
    }

    public void updateMetadata(Long id, JsonNode metadata) {
        this.baseMapper.updateMetadata(id, metadata.toString());
    }

    public List<Map<String, Object>> tagDistribution() {
        return this.baseMapper.tagDistribution();
    }

    public List<KnowledgeVectorSearchDTO> searchBySemantic(String scene, String queryEmbedding, int limit) {
        return this.baseMapper.searchBySemantic(scene, queryEmbedding, limit);
    }

    public List<KnowledgeVectorSearchDTO> searchBySemantic(List<String> scenes, String queryEmbedding, int limit) {
        return this.baseMapper.searchBySemanticScenes(scenes, queryEmbedding, limit);
    }

    public Map<String, Object> stats() {
        long chunkCount = this.count();
        long taskCount = this.baseMapper.countDistinctTaskNo();
        long totalTextLength = this.baseMapper.sumTextLength();
        OffsetDateTime latest = this.lambdaQuery()
                .select(KnowledgeVectorPO::getCreatedAt)
                .orderByDesc(KnowledgeVectorPO::getCreatedAt)
                .last("LIMIT 1")
                .oneOpt()
                .map(KnowledgeVectorPO::getCreatedAt)
                .orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("taskCount", taskCount);
        result.put("chunkCount", chunkCount);
        result.put("totalTextLength", totalTextLength);
        result.put("latestCreatedAt", latest);
        return result;
    }
}
