package com.scrapider.finance.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkPageVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeStatsVO;
import com.scrapider.finance.ai.service.KnowledgeService;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import com.scrapider.finance.manage.KnowledgeVectorManage;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final KnowledgeVectorManage knowledgeVectorManage;

    public KnowledgeServiceImpl(KnowledgeVectorManage knowledgeVectorManage) {
        this.knowledgeVectorManage = knowledgeVectorManage;
    }

    @Override
    public KnowledgeStatsVO stats() {
        Map<String, Object> stats = this.knowledgeVectorManage.stats();
        return new KnowledgeStatsVO(
                ((Number) stats.get("taskCount")).longValue(),
                ((Number) stats.get("chunkCount")).longValue(),
                ((Number) stats.get("totalTextLength")).longValue(),
                (OffsetDateTime) stats.get("latestCreatedAt"));
    }

    @Override
    public KnowledgeChunkPageVO pageChunks(int pageNum, int pageSize) {
        int pn = Math.max(pageNum, DEFAULT_PAGE_NUM);
        int ps = Math.min(Math.max(pageSize, DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);
        Page<KnowledgeVectorPO> page = this.knowledgeVectorManage.pageChunks(pn, ps);
        return KnowledgeChunkPageVO.fromPage(page);
    }

    @Override
    public KnowledgeChunkVO chunkDetail(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        KnowledgeVectorPO po = this.knowledgeVectorManage.findById(id);
        if (po == null) {
            throw new IllegalArgumentException("知识条目不存在");
        }
        return KnowledgeChunkVO.fromPO(po);
    }
}
