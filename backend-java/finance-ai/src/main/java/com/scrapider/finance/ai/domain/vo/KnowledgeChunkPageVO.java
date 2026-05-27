package com.scrapider.finance.ai.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import java.util.List;

public record KnowledgeChunkPageVO(
        List<KnowledgeChunkVO> records,
        long total,
        long pageNum,
        long pageSize,
        long pages) {

    public static KnowledgeChunkPageVO fromPage(Page<KnowledgeVectorPO> page) {
        return new KnowledgeChunkPageVO(
                page.getRecords().stream().map(KnowledgeChunkVO::fromPO).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages());
    }
}
