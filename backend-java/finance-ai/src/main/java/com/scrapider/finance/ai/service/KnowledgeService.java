package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.vo.KnowledgeChunkPageVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeChunkVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeOverviewVO;
import com.scrapider.finance.ai.domain.vo.KnowledgeStatsVO;
import com.scrapider.finance.domain.param.KnowledgeChunkUpdateParam;

public interface KnowledgeService {

    KnowledgeStatsVO stats();

    KnowledgeOverviewVO overview();

    KnowledgeChunkPageVO pageChunks(int pageNum, int pageSize, String filename, String sourceType,
            String category, String tag);

    KnowledgeChunkVO chunkDetail(Long id);

    KnowledgeChunkVO updateChunk(Long id, KnowledgeChunkUpdateParam param);
}
