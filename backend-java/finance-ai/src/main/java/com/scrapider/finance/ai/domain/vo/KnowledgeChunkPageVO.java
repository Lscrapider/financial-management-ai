package com.scrapider.finance.ai.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scrapider.finance.domain.po.KnowledgeVectorPO;
import java.util.List;
import java.util.Map;

public record KnowledgeChunkPageVO(
        List<KnowledgeChunkVO> records,
        long total,
        long pageNum,
        long pageSize,
        long pages) {

    public static KnowledgeChunkPageVO fromPage(Page<KnowledgeVectorPO> page) {
        return fromPage(page, Map.of());
    }

    public static KnowledgeChunkPageVO fromPage(
            Page<KnowledgeVectorPO> page,
            Map<String, String> filenameMap) {
        long total = page.getTotal() > 0 ? page.getTotal() : page.getRecords().size();
        long pages = page.getPages() > 0
                ? page.getPages()
                : (long) Math.ceil((double) total / page.getSize());
        return new KnowledgeChunkPageVO(
                page.getRecords().stream()
                        .map(po -> KnowledgeChunkVO.fromPO(po, filenameMap.get(po.getTaskNo())))
                        .toList(),
                total,
                page.getCurrent(),
                page.getSize(),
                pages);
    }
}
