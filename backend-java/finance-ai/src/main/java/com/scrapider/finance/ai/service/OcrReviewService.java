package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.param.OcrReviewDraftParam;
import com.scrapider.finance.ai.domain.vo.OcrReviewVO;

public interface OcrReviewService {

    OcrReviewVO detail(String taskNo);

    void saveDraft(String taskNo, OcrReviewDraftParam param);

    void submit(String taskNo, OcrReviewDraftParam param);

    byte[] pageImage(String taskNo, Integer pageNo);
}
