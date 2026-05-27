package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import com.scrapider.finance.mapper.KnowledgeVectorMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeVectorManage extends ServiceImpl<KnowledgeVectorMapper, KnowledgeVectorPO> {

    public Page<KnowledgeVectorPO> pageChunks(int pageNum, int pageSize) {
        return this.lambdaQuery()
                .orderByAsc(KnowledgeVectorPO::getTaskNo)
                .orderByAsc(KnowledgeVectorPO::getChunkIndex)
                .page(Page.of(pageNum, pageSize));
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
