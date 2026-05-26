package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.OcrQualityValidateMessageDTO;

public interface OcrReviewInitializationService {

    void initialize(OcrQualityValidateMessageDTO message);
}
