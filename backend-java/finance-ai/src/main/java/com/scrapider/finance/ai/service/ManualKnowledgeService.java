package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.ManualKnowledgeDraftParam;
import com.scrapider.finance.ai.domain.param.OcrTaskDeleteParam;
import com.scrapider.finance.ai.domain.param.OcrTaskPageParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskPageVO;
import com.scrapider.finance.ai.domain.vo.OcrTaskVO;

public interface ManualKnowledgeService {

    OcrTaskPageVO page(OcrTaskPageParam param);

    OcrTaskVO createDraft(ManualKnowledgeDraftParam param);

    OcrReviewVO detail(String taskNo);

    void saveDraft(String taskNo, ManualKnowledgeDraftParam param);

    void submit(String taskNo, ManualKnowledgeDraftParam param);

    void delete(OcrTaskDeleteParam param);
}
