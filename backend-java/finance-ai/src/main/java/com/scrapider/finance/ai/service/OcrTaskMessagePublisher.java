package com.scrapider.finance.ai.service;

import com.scrapider.finance.ai.domain.dto.OcrNormalizeMessageDTO;

public interface OcrTaskMessagePublisher {

    void publishNormalizeMessage(OcrNormalizeMessageDTO message);
}
