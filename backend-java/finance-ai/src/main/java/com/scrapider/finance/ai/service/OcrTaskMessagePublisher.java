package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.KnowledgeReembedMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrChunkTagRuleMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrEmbeddingIndexMessageDTO;
import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;

public interface OcrTaskMessagePublisher {

    void publishNormalizeMessage(OcrNormalizeMessageDTO message);

    void publishChunkTagRuleMessage(OcrChunkTagRuleMessageDTO message);

    void publishEmbeddingIndexMessage(OcrEmbeddingIndexMessageDTO message);

    void publishReembedMessage(KnowledgeReembedMessageDTO message);
}
