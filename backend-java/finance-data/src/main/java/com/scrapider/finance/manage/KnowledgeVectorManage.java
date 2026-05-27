package com.scrapider.finance.manage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import com.scrapider.finance.mapper.KnowledgeVectorMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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

    public Map<String, Object> stats() {
        List<KnowledgeVectorPO> all = this.lambdaQuery()
                .select(KnowledgeVectorPO::getTaskNo,
                        KnowledgeVectorPO::getId,
                        KnowledgeVectorPO::getText,
                        KnowledgeVectorPO::getCreatedAt)
                .list();
        long chunkCount = all.size();
        long taskCount = all.stream()
                .map(KnowledgeVectorPO::getTaskNo)
                .distinct()
                .count();
        long totalTextLength = all.stream()
                .mapToLong(chunk -> chunk.getText() != null ? chunk.getText().length() : 0)
                .sum();
        OffsetDateTime latest = all.stream()
                .map(KnowledgeVectorPO::getCreatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("taskCount", taskCount);
        result.put("chunkCount", chunkCount);
        result.put("totalTextLength", totalTextLength);
        result.put("latestCreatedAt", latest);
        return result;
    }
}
